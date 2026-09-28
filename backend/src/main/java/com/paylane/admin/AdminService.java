package com.paylane.admin;

import com.paylane.admin.AdminDtos.AdminUserResponse;
import com.paylane.admin.AdminDtos.StatsResponse;
import com.paylane.common.ApiException;
import com.paylane.common.Money;
import com.paylane.common.PageResponse;
import com.paylane.config.AppProperties;
import com.paylane.notification.NotificationService;
import com.paylane.transaction.LedgerService;
import com.paylane.transaction.Transaction;
import com.paylane.transaction.TransactionDtos.AdminTransactionResponse;
import com.paylane.transaction.TransactionMapper;
import com.paylane.transaction.TransactionRepository;
import com.paylane.transaction.TransactionSpecs;
import com.paylane.transaction.TransactionStatus;
import com.paylane.transaction.TransactionType;
import com.paylane.user.Role;
import com.paylane.user.User;
import com.paylane.user.UserRepository;
import com.paylane.user.UserStatus;
import com.paylane.wallet.Wallet;
import com.paylane.wallet.WalletRepository;
import com.paylane.wallet.WalletStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final AppProperties props;

    public AdminService(UserRepository users, WalletRepository wallets, TransactionRepository transactions,
                        TransactionMapper mapper, LedgerService ledger, NotificationService notifications,
                        AppProperties props) {
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.mapper = mapper;
        this.ledger = ledger;
        this.notifications = notifications;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        var today = LedgerService.startOfTodayUtc();
        var month = LedgerService.startOfMonthUtc();
        return new StatsResponse(
                users.count(),
                users.countByStatus(UserStatus.FROZEN),
                Money.orZero(wallets.totalBalance()),
                transactions.countByCreatedAtGreaterThanEqual(today),
                transactions.countByStatusAndCreatedAtGreaterThanEqual(TransactionStatus.FAILED, today),
                Money.orZero(transactions.completedVolumeSince(today)),
                Money.orZero(transactions.completedVolumeSince(month)),
                props.wallet().currency());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> users(String search, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> result = search == null || search.isBlank() ? users.findAll(pr) : users.search(search.trim(), pr);
        return PageResponse.of(result, this::toAdminUser);
    }

    @Transactional
    public AdminUserResponse setStatus(Long adminId, Long userId, UserStatus status) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getId().equals(adminId)) {
            throw ApiException.badRequest("SELF_STATUS", "You can't change your own status");
        }
        if (user.getRole() == Role.ADMIN && status == UserStatus.FROZEN) {
            throw ApiException.badRequest("ADMIN_FREEZE", "Admin accounts can't be frozen");
        }
        user.setStatus(status);
        wallets.findByUserId(userId).ifPresent(w ->
                w.setStatus(status == UserStatus.FROZEN ? WalletStatus.FROZEN : WalletStatus.ACTIVE));
        notifications.notify(user, status == UserStatus.FROZEN ? "Account frozen" : "Account restored",
                status == UserStatus.FROZEN
                        ? "Your account has been frozen by support."
                        : "Your account is active again.");
        return toAdminUser(user);
    }

    @Transactional
    public AdminUserResponse setDailyLimit(Long userId, BigDecimal limit) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        Wallet wallet = wallets.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet not found"));
        wallet.setDailyLimit(Money.orZero(limit));
        return toAdminUser(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> transactions(TransactionType type, TransactionStatus status,
                                                               String reference, int page, int size) {
        Specification<Transaction> spec = Specification.where(TransactionSpecs.hasType(type))
                .and(TransactionSpecs.hasStatus(status))
                .and(TransactionSpecs.referenceLike(reference));
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(transactions.findAll(spec, pr), mapper::toAdmin);
    }

    /** Sends a completed transfer back from the receiver to the sender. */
    @Transactional
    public AdminTransactionResponse reverse(Long adminId, String reference, String reason) {
        Transaction original = transactions.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        if (original.getType() != TransactionType.TRANSFER) {
            throw ApiException.unprocessable("NOT_REVERSIBLE", "Only wallet-to-wallet transfers can be reversed");
        }
        if (original.getStatus() != TransactionStatus.COMPLETED || transactions.existsByReversalOfId(original.getId())) {
            throw ApiException.conflict("ALREADY_REVERSED", "This transaction is not in a reversible state");
        }
        User admin = users.findById(adminId).orElseThrow(() -> ApiException.notFound("Admin not found"));
        List<Wallet> locked = ledger.lockPair(original.getSenderWallet().getId(), original.getReceiverWallet().getId());
        Wallet originalSender = locked.get(0);
        Wallet originalReceiver = locked.get(1);
        BigDecimal amount = original.getAmount();

        Transaction reversal = ledger.newTransaction(TransactionType.REVERSAL, amount, admin, null);
        reversal.setSenderWallet(originalReceiver);
        reversal.setReceiverWallet(originalSender);
        reversal.setReversalOf(original);
        reversal.setDescription("Reversal of " + original.getReference() + ": " + reason.trim());
        transactions.save(reversal);

        ledger.reversalDebit(originalReceiver, reversal, amount);
        ledger.reversalCredit(originalSender, reversal, amount);
        reversal.markCompleted();
        original.markReversed();

        String money = original.getCurrency() + " " + amount;
        notifications.notify(originalSender.getUser(), "Payment reversed",
                money + " from " + original.getReference() + " was returned to your wallet.");
        notifications.notify(originalReceiver.getUser(), "Payment reversed",
                money + " from " + original.getReference() + " was reversed by support.");
        return mapper.toAdmin(reversal);
    }

    private AdminUserResponse toAdminUser(User u) {
        Wallet w = wallets.findByUserId(u.getId()).orElse(null);
        return new AdminUserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.getStatus(), w == null ? null : w.getBalance(), w == null ? null : w.getDailyLimit(),
                u.getCreatedAt());
    }
}
