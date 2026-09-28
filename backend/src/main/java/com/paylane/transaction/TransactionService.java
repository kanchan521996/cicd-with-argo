package com.paylane.transaction;

import com.paylane.common.ApiException;
import com.paylane.common.Money;
import com.paylane.common.PageResponse;
import com.paylane.config.AppProperties;
import com.paylane.transaction.TransactionDtos.SummaryResponse;
import com.paylane.transaction.TransactionDtos.TransactionResponse;
import com.paylane.wallet.WalletRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final WalletRepository wallets;
    private final TransactionMapper mapper;
    private final AppProperties props;

    public TransactionService(TransactionRepository transactions, WalletRepository wallets,
                              TransactionMapper mapper, AppProperties props) {
        this.transactions = transactions;
        this.wallets = wallets;
        this.mapper = mapper;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long userId, TransactionType type, TransactionStatus status,
                                                     LocalDate from, LocalDate to, String reference,
                                                     int page, int size) {
        Long walletId = walletId(userId);
        Instant fromTs = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toTs = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Specification<Transaction> spec = Specification.where(TransactionSpecs.involvesWallet(walletId))
                .and(TransactionSpecs.hasType(type))
                .and(TransactionSpecs.hasStatus(status))
                .and(TransactionSpecs.createdFrom(fromTs))
                .and(TransactionSpecs.createdBefore(toTs))
                .and(TransactionSpecs.referenceLike(reference));
        PageRequest pr = PageRequest.of(Math.max(page, 0), clamp(size),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<Transaction> result = transactions.findAll(spec, pr);
        return PageResponse.of(result, t -> mapper.toResponse(t, walletId));
    }

    @Transactional(readOnly = true)
    public TransactionResponse byReference(Long userId, String reference) {
        Long walletId = walletId(userId);
        Transaction t = transactions.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
        boolean mine = (t.getSenderWallet() != null && t.getSenderWallet().getId().equals(walletId))
                || (t.getReceiverWallet() != null && t.getReceiverWallet().getId().equals(walletId));
        if (!mine) {
            // Same response as "missing" so references can't be probed.
            throw ApiException.notFound("Transaction not found");
        }
        return mapper.toResponse(t, walletId);
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId) {
        Long walletId = walletId(userId);
        Instant monthStart = LedgerService.startOfMonthUtc();
        return new SummaryResponse(
                Money.orZero(transactions.sumCredits(walletId, monthStart)),
                Money.orZero(transactions.sumAllDebits(walletId, monthStart)),
                props.wallet().currency());
    }

    private Long walletId(Long userId) {
        return wallets.findIdByUserId(userId).orElseThrow(() -> ApiException.notFound("Wallet not found"));
    }

    static int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
