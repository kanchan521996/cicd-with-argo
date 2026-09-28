package com.paylane.transaction;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("select e.balanceAfter from LedgerEntry e where e.transaction.id = :txId and e.wallet.id = :walletId")
    Optional<BigDecimal> balanceAfter(@Param("txId") Long transactionId, @Param("walletId") Long walletId);
}
