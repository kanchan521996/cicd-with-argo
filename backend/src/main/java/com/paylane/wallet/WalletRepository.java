package com.paylane.wallet;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    /** Id only, so the entity is not loaded before it is locked. */
    @Query("select w.id from Wallet w where w.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    @Query("select sum(w.balance) from Wallet w")
    BigDecimal totalBalance();
}
