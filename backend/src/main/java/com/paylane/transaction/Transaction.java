package com.paylane.transaction;

import com.paylane.bill.Biller;
import com.paylane.paymentmethod.PaymentMethod;
import com.paylane.user.User;
import com.paylane.wallet.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_wallet_id")
    private Wallet senderWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_wallet_id")
    private Wallet receiverWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "biller_id")
    private Biller biller;

    @Column(name = "bill_account_ref")
    private String billAccountRef;

    private String description;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by", nullable = false, updatable = false)
    private User initiatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private Transaction reversalOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markCompleted() {
        status = TransactionStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        status = TransactionStatus.FAILED;
        failureReason = reason;
        completedAt = Instant.now();
    }

    public void markReversed() {
        status = TransactionStatus.REVERSED;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public TransactionStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Wallet getSenderWallet() { return senderWallet; }
    public void setSenderWallet(Wallet senderWallet) { this.senderWallet = senderWallet; }
    public Wallet getReceiverWallet() { return receiverWallet; }
    public void setReceiverWallet(Wallet receiverWallet) { this.receiverWallet = receiverWallet; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public Biller getBiller() { return biller; }
    public void setBiller(Biller biller) { this.biller = biller; }
    public String getBillAccountRef() { return billAccountRef; }
    public void setBillAccountRef(String billAccountRef) { this.billAccountRef = billAccountRef; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFailureReason() { return failureReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public User getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(User initiatedBy) { this.initiatedBy = initiatedBy; }
    public Transaction getReversalOf() { return reversalOf; }
    public void setReversalOf(Transaction reversalOf) { this.reversalOf = reversalOf; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
