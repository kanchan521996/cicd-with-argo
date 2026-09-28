package com.paylane.bill;

import com.paylane.bill.BillDtos.BillerResponse;
import com.paylane.bill.BillDtos.PayBillRequest;
import com.paylane.common.ApiException;
import com.paylane.notification.NotificationService;
import com.paylane.transaction.LedgerService;
import com.paylane.transaction.Transaction;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.transaction.TransactionMapper;
import com.paylane.transaction.TransactionRepository;
import com.paylane.transaction.TransactionType;
import com.paylane.user.User;
import com.paylane.user.UserService;
import com.paylane.wallet.Wallet;
import com.paylane.wallet.WalletService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillService {

    private final BillerRepository billers;
    private final LedgerService ledger;
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;
    private final UserService userService;
    private final WalletService walletService;
    private final NotificationService notifications;

    public BillService(BillerRepository billers, LedgerService ledger, TransactionRepository transactions,
                       TransactionMapper mapper, UserService userService, WalletService walletService,
                       NotificationService notifications) {
        this.billers = billers;
        this.ledger = ledger;
        this.transactions = transactions;
        this.mapper = mapper;
        this.userService = userService;
        this.walletService = walletService;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<BillerResponse> billers() {
        return billers.findByActiveTrueOrderByCategoryAscNameAsc().stream().map(BillerResponse::from).toList();
    }

    @Transactional
    public TransactionResponse pay(Long userId, PayBillRequest req, String idempotencyKey) {
        Optional<Transaction> replay = ledger.findIdempotent(userId, idempotencyKey);
        if (replay.isPresent()) {
            return mapper.toResponse(replay.get(), walletService.walletIdOf(userId));
        }
        User user = userService.getActive(userId);
        Biller biller = billers.findById(req.billerId())
                .filter(Biller::isActive)
                .orElseThrow(() -> ApiException.notFound("Biller not found"));
        BigDecimal amount = ledger.validateAmount(req.amount());

        Long walletId = walletService.walletIdOf(userId);
        Wallet wallet = ledger.lock(walletId);
        wallet.ensureActive();
        ledger.checkDailyLimit(wallet, amount);

        Transaction t = ledger.newTransaction(TransactionType.BILL_PAYMENT, amount, user, idempotencyKey);
        t.setSenderWallet(wallet);
        t.setBiller(biller);
        t.setBillAccountRef(req.accountReference().trim());
        t.setDescription(biller.getName() + " \u2013 " + biller.getAccountLabel() + " " + t.getBillAccountRef());
        transactions.save(t);

        ledger.debit(wallet, t, amount);
        t.markCompleted();
        notifications.notify(user, "Bill paid",
                "You paid " + wallet.getCurrency() + " " + amount + " to " + biller.getName() + ".");
        return mapper.toResponse(t, walletId);
    }
}
