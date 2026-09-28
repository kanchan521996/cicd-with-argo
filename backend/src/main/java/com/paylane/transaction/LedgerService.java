package com.paylane.transaction;

import com.paylane.common.ApiException;
import com.paylane.common.Money;
import com.paylane.common.ReferenceGenerator;
import com.paylane.config.AppProperties;
import com.paylane.user.User;
import com.paylane.wallet.Wallet;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Low-level money movement. Every method expects to run inside the caller's transaction.
 */
@Service
public class LedgerService {

    static final Set<TransactionType> SPEND_TYPES =
            EnumSet.of(TransactionType.TRANSFER, TransactionType.WITHDRAWAL, TransactionType.BILL_PAYMENT);

    private final EntityManager em;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AppProperties props;

    public LedgerService(EntityManager em, TransactionRepository transactions, LedgerEntryRepository entries,
                         AppProperties props) {
        this.em = em;
        this.transactions = transactions;
        this.entries = entries;
        this.props = props;
    }

    /** SELECT ... FOR UPDATE, always re-reading the row so the balance is never stale. */
    public Wallet lock(Long walletId) {
        Wallet wallet = em.find(Wallet.class, walletId);
        if (wallet == null) {
            throw ApiException.notFound("Wallet not found");
        }
        em.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
        return wallet;
    }

    /** Locks two wallets in id order so concurrent A->B and B->A transfers cannot deadlock. */
    public List<Wallet> lockPair(Long firstId, Long secondId) {
        if (firstId < secondId) {
            Wallet a = lock(firstId);
            Wallet b = lock(secondId);
            return List.of(a, b);
        }
        Wallet b = lock(secondId);
        Wallet a = lock(firstId);
        return List.of(a, b);
    }

    public BigDecimal validateAmount(BigDecimal raw) {
        BigDecimal amount = Money.normalize(raw);
        BigDecimal max = props.wallet().maxTransactionAmount();
        if (max != null && amount.compareTo(max) > 0) {
            throw ApiException.badRequest("AMOUNT_TOO_LARGE", "The maximum per transaction is " + max);
        }
        return amount;
    }

    public Transaction newTransaction(TransactionType type, BigDecimal amount, User initiatedBy, String idempotencyKey) {
        Transaction t = new Transaction();
        t.setReference(ReferenceGenerator.next());
        t.setType(type);
        t.setAmount(amount);
        t.setCurrency(props.wallet().currency());
        t.setInitiatedBy(initiatedBy);
        t.setIdempotencyKey(normalizeKey(idempotencyKey));
        return t;
    }

    public Optional<Transaction> findIdempotent(Long userId, String idempotencyKey) {
        String key = normalizeKey(idempotencyKey);
        return key == null ? Optional.empty() : transactions.findByInitiatedByIdAndIdempotencyKey(userId, key);
    }

    public BigDecimal spentToday(Long walletId) {
        return Money.orZero(transactions.sumDebits(walletId, SPEND_TYPES, startOfTodayUtc()));
    }

    public void checkDailyLimit(Wallet wallet, BigDecimal amount) {
        BigDecimal spent = spentToday(wallet.getId());
        if (spent.add(amount).compareTo(wallet.getDailyLimit()) > 0) {
            BigDecimal left = wallet.getDailyLimit().subtract(spent).max(BigDecimal.ZERO);
            throw ApiException.unprocessable("DAILY_LIMIT_EXCEEDED",
                    "This exceeds your daily limit. You can still send " + wallet.getCurrency() + " " + left + " today.");
        }
    }

    public void debit(Wallet wallet, Transaction t, BigDecimal amount) {
        wallet.debit(amount);
        entries.save(new LedgerEntry(t, wallet, EntryType.DEBIT, amount, wallet.getBalance()));
    }

    public void credit(Wallet wallet, Transaction t, BigDecimal amount) {
        wallet.credit(amount);
        entries.save(new LedgerEntry(t, wallet, EntryType.CREDIT, amount, wallet.getBalance()));
    }

    public void reversalDebit(Wallet wallet, Transaction t, BigDecimal amount) {
        wallet.debitForReversal(amount);
        entries.save(new LedgerEntry(t, wallet, EntryType.DEBIT, amount, wallet.getBalance()));
    }

    public void reversalCredit(Wallet wallet, Transaction t, BigDecimal amount) {
        wallet.creditForReversal(amount);
        entries.save(new LedgerEntry(t, wallet, EntryType.CREDIT, amount, wallet.getBalance()));
    }

    public static Instant startOfTodayUtc() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public static Instant startOfMonthUtc() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        if (k.length() > 64) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 64 characters or fewer");
        }
        return k;
    }
}
