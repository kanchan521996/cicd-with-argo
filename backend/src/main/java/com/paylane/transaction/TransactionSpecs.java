package com.paylane.transaction;

import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecs {

    private TransactionSpecs() {
    }

    public static Specification<Transaction> involvesWallet(Long walletId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("senderWallet").get("id"), walletId),
                cb.equal(root.get("receiverWallet").get("id"), walletId));
    }

    public static Specification<Transaction> hasType(TransactionType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> createdFrom(Instant from) {
        return from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Transaction> createdBefore(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }

    public static Specification<Transaction> referenceLike(String reference) {
        return reference == null || reference.isBlank() ? null
                : (root, query, cb) -> cb.like(root.get("reference"), "%" + reference.trim().toUpperCase() + "%");
    }
}
