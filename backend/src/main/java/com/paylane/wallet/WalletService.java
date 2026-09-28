package com.paylane.wallet;

import com.paylane.common.ApiException;
import com.paylane.config.AppProperties;
import com.paylane.gateway.GatewayResult;
import com.paylane.gateway.PaymentGateway;
import com.paylane.notification.NotificationService;
import com.paylane.paymentmethod.PaymentMethod;
import com.paylane.paymentmethod.PaymentMethodService;
import com.paylane.paymentmethod.PaymentMethodType;
import com.paylane.transaction.LedgerService;
import com.paylane.transaction.Transaction;
import com.paylane.transaction.TransactionMapper;
import com.paylane.transaction.TransactionRepository;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.transaction.TransactionType;
import com.paylane.user.User;
import com.paylane.user.UserService;
import com.paylane.wallet.WalletDtos.TopUpRequest;
import com.paylane.wallet.WalletDtos.WalletResponse;
import com.paylane.wallet.WalletDtos.WithdrawRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final AppProperties props;
    private final LedgerService ledger;
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;
    private final PaymentMethodService paymentMethods;
    private final PaymentGateway gateway;
    private final UserService userService;
    private final NotificationService notifications;

    public WalletService(WalletRepository wallets, AppProperties props, LedgerService ledger,
                         TransactionRepository transactions, TransactionMapper mapper,
                         PaymentMethodService paymentMethods, PaymentGateway gateway,
                         UserService userService, NotificationService notifications) {
        this.wallets = wallets;
        this.props = props;
        this.ledger = ledger;
        this.transactions = transactions;
        this.mapper = mapper;
        this.paymentMethods = paymentMethods;
        this.gateway = gateway;
        this.userService = userService;
        this.notifications = notifications;
    }

    @Transactional
    public Wallet createWalletFor(User user) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setCurrency(props.wallet().currency());
        wallet.setDailyLimit(props.wallet().defaultDailyLimit());
        return wallets.save(wallet);
    }

    public Long walletIdOf(Long userId) {
        return wallets.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet not found"));
    }

    @Transactional(readOnly = true)
    public WalletResponse myWallet(Long userId) {
        Wallet w = wallets.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet not found"));
        BigDecimal spent = ledger.spentToday(w.getId());
        BigDecimal remaining = w.getDailyLimit().subtract(spent).max(BigDecimal.ZERO);
        return new WalletResponse(w.getId(), w.getBalance(), w.getCurrency(), w.getStatus(), w.getDailyLimit(),
                spent, remaining);
    }

    /** Add money from a saved card. A declined card is recorded as a FAILED transaction. */
    @Transactional
    public TransactionResponse topUp(Long userId, TopUpRequest req, String idempotencyKey) {
        Optional<Transaction> replay = ledger.findIdempotent(userId, idempotencyKey);
        if (replay.isPresent()) {
            return mapper.toResponse(replay.get(), walletIdOf(userId));
        }
        User user = userService.getActive(userId);
        BigDecimal amount = ledger.validateAmount(req.amount());
        PaymentMethod pm = paymentMethods.getOwned(userId, req.paymentMethodId());
        if (pm.getType() != PaymentMethodType.CARD) {
            throw ApiException.badRequest("CARD_REQUIRED", "Add money using a debit or credit card");
        }
        Long walletId = walletIdOf(userId);

        Transaction t = ledger.newTransaction(TransactionType.TOPUP, amount, user, idempotencyKey);
        t.setPaymentMethod(pm);
        t.setDescription("Added money from " + pm.label());
        transactions.save(t);

        // Charge the card before taking the wallet lock so a slow gateway doesn't block other payments.
        GatewayResult result = gateway.charge(pm, amount, t.getReference());
        Wallet wallet = ledger.lock(walletId);
        t.setReceiverWallet(wallet);
        if (result.success()) {
            ledger.credit(wallet, t, amount);
            t.markCompleted();
            notifications.notify(user, "Money added",
                    "You added " + wallet.getCurrency() + " " + amount + " from " + pm.label() + ".");
        } else {
            t.markFailed(result.message());
        }
        return mapper.toResponse(t, walletId);
    }

    /** Move money out to a linked bank account. PIN already verified by the controller. */
    @Transactional
    public TransactionResponse withdraw(Long userId, WithdrawRequest req, String idempotencyKey) {
        Optional<Transaction> replay = ledger.findIdempotent(userId, idempotencyKey);
        if (replay.isPresent()) {
            return mapper.toResponse(replay.get(), walletIdOf(userId));
        }
        User user = userService.getActive(userId);
        BigDecimal amount = ledger.validateAmount(req.amount());
        PaymentMethod pm = paymentMethods.getOwned(userId, req.paymentMethodId());
        if (pm.getType() != PaymentMethodType.BANK_ACCOUNT) {
            throw ApiException.badRequest("BANK_REQUIRED", "Withdraw to a linked bank account");
        }
        Long walletId = walletIdOf(userId);
        Wallet wallet = ledger.lock(walletId);
        wallet.ensureActive();
        ledger.checkDailyLimit(wallet, amount);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "Your balance is too low for this withdrawal");
        }

        Transaction t = ledger.newTransaction(TransactionType.WITHDRAWAL, amount, user, idempotencyKey);
        t.setSenderWallet(wallet);
        t.setPaymentMethod(pm);
        t.setDescription("Withdrawal to " + pm.label());
        transactions.save(t);

        GatewayResult result = gateway.payout(pm, amount, t.getReference());
        if (result.success()) {
            ledger.debit(wallet, t, amount);
            t.markCompleted();
            notifications.notify(user, "Withdrawal sent",
                    wallet.getCurrency() + " " + amount + " is on its way to " + pm.label() + ".");
        } else {
            t.markFailed(result.message());
        }
        return mapper.toResponse(t, walletId);
    }
}
