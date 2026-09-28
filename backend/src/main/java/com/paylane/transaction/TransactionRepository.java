package com.paylane.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByInitiatedByIdAndIdempotencyKey(Long userId, String idempotencyKey);

    boolean existsByReversalOfId(Long transactionId);

    @Query("select sum(t.amount) from Transaction t where t.senderWallet.id = :walletId "
            + "and t.status = com.paylane.transaction.TransactionStatus.COMPLETED "
            + "and t.type in :types and t.createdAt >= :since")
    BigDecimal sumDebits(@Param("walletId") Long walletId,
                         @Param("types") Collection<TransactionType> types,
                         @Param("since") Instant since);

    @Query("select sum(t.amount) from Transaction t where t.receiverWallet.id = :walletId "
            + "and t.status = com.paylane.transaction.TransactionStatus.COMPLETED and t.createdAt >= :since")
    BigDecimal sumCredits(@Param("walletId") Long walletId, @Param("since") Instant since);

    @Query("select sum(t.amount) from Transaction t where t.senderWallet.id = :walletId "
            + "and t.status = com.paylane.transaction.TransactionStatus.COMPLETED and t.createdAt >= :since")
    BigDecimal sumAllDebits(@Param("walletId") Long walletId, @Param("since") Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByStatusAndCreatedAtGreaterThanEqual(TransactionStatus status, Instant since);

    @Query("select sum(t.amount) from Transaction t where t.status = com.paylane.transaction.TransactionStatus.COMPLETED "
            + "and t.createdAt >= :since")
    BigDecimal completedVolumeSince(@Param("since") Instant since);
}
