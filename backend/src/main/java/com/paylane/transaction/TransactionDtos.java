package com.paylane.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransferRequest(
            @NotBlank String recipient,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 140) String note,
            @NotBlank String pin
    ) {
    }

    /** A transaction as seen by one wallet owner. */
    public record TransactionResponse(
            Long id,
            String reference,
            TransactionType type,
            TransactionStatus status,
            Direction direction,
            BigDecimal amount,
            String currency,
            String counterparty,
            String counterpartyDetail,
            String description,
            String failureReason,
            BigDecimal balanceAfter,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record SummaryResponse(
            BigDecimal receivedThisMonth,
            BigDecimal spentThisMonth,
            String currency
    ) {
    }

    /** Admin view with both sides of the transaction. */
    public record AdminTransactionResponse(
            Long id,
            String reference,
            TransactionType type,
            TransactionStatus status,
            BigDecimal amount,
            String currency,
            String sender,
            String receiver,
            String description,
            String failureReason,
            String reversalOf,
            Instant createdAt
    ) {
    }
}
