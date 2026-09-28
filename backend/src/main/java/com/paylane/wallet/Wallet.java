package com.paylane.wallet;

import com.paylane.common.ApiException;
import com.paylane.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void ensureActive() {
        if (status != WalletStatus.ACTIVE) {
            throw ApiException.forbidden("WALLET_FROZEN", "This wallet is frozen");
        }
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        if (balance.compareTo(amount) < 0) {
            throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "Your balance is too low for this payment");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        balance = balance.add(amount);
    }

    /** Admin reversal: allowed on frozen wallets (e.g. fraud recovery) but never below zero. */
    public void debitForReversal(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw ApiException.unprocessable("INSUFFICIENT_FUNDS",
                    "The receiver no longer has enough balance to reverse this payment");
        }
        balance = balance.subtract(amount);
    }

    public void creditForReversal(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }
    public WalletStatus getStatus() { return status; }
    public void setStatus(WalletStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
