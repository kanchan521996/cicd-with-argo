package com.paylane.transaction;

import com.paylane.common.ApiException;
import com.paylane.notification.NotificationService;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.transaction.TransactionDtos.TransferRequest;
import com.paylane.user.User;
import com.paylane.user.UserService;
import com.paylane.user.UserStatus;
import com.paylane.wallet.Wallet;
import com.paylane.wallet.WalletService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final LedgerService ledger;
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;
    private final UserService userService;
    private final WalletService walletService;
    private final NotificationService notifications;

    public TransferService(LedgerService ledger, TransactionRepository transactions, TransactionMapper mapper,
                           UserService userService, WalletService walletService, NotificationService notifications) {
        this.ledger = ledger;
        this.transactions = transactions;
        this.mapper = mapper;
        this.userService = userService;
        this.walletService = walletService;
        this.notifications = notifications;
    }

    @Transactional
    public TransactionResponse send(Long userId, TransferRequest req, String idempotencyKey) {
        Optional<Transaction> replay = ledger.findIdempotent(userId, idempotencyKey);
        if (replay.isPresent()) {
            return mapper.toResponse(replay.get(), walletService.walletIdOf(userId));
        }
        User sender = userService.getActive(userId);
        User recipient = userService.findByIdentifier(req.recipient());
        Transaction t = execute(sender, recipient, req.amount(), req.note(), idempotencyKey);
        return mapper.toResponse(t, t.getSenderWallet().getId());
    }

    /**
     * Wallet-to-wallet move. Shared by direct transfers and paying a money request.
     * Caller must be inside a transaction and must already have checked the PIN.
     */
    public Transaction execute(User sender, User recipient, BigDecimal rawAmount, String note, String idempotencyKey) {
        if (sender.getId().equals(recipient.getId())) {
            throw ApiException.badRequest("SELF_TRANSFER", "You can't send money to yourself");
        }
        if (recipient.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unprocessable("RECIPIENT_UNAVAILABLE", "This account can't receive payments right now");
        }
        BigDecimal amount = ledger.validateAmount(rawAmount);
        Long senderWalletId = walletService.walletIdOf(sender.getId());
        Long recipientWalletId = walletService.walletIdOf(recipient.getId());

        List<Wallet> locked = ledger.lockPair(senderWalletId, recipientWalletId);
        Wallet from = locked.get(0);
        Wallet to = locked.get(1);
        from.ensureActive();
        to.ensureActive();
        ledger.checkDailyLimit(from, amount);

        Transaction t = ledger.newTransaction(TransactionType.TRANSFER, amount, sender, idempotencyKey);
        t.setSenderWallet(from);
        t.setReceiverWallet(to);
        t.setDescription(note == null || note.isBlank() ? null : note.trim());
        transactions.save(t);

        ledger.debit(from, t, amount);
        ledger.credit(to, t, amount);
        t.markCompleted();

        String money = from.getCurrency() + " " + amount;
        notifications.notify(sender, "Payment sent", "You sent " + money + " to " + recipient.getFullName() + ".");
        notifications.notify(recipient, "Payment received",
                sender.getFullName() + " sent you " + money + (t.getDescription() == null ? "." : ": " + t.getDescription()));
        return t;
    }
}
