package com.paylane.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class MoneyRequestDtos {

    private MoneyRequestDtos() {
    }

    public record CreateMoneyRequest(
            @NotBlank String payer,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 140) String note
    ) {
    }

    public record PayMoneyRequest(@NotBlank String pin) {
    }

    public record MoneyRequestResponse(
            Long id,
            Long requesterId,
            String requesterName,
            String requesterEmail,
            Long payerId,
            String payerName,
            String payerEmail,
            BigDecimal amount,
            String currency,
            String note,
            MoneyRequestStatus status,
            String transactionReference,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static MoneyRequestResponse from(MoneyRequest r) {
            return new MoneyRequestResponse(r.getId(),
                    r.getRequester().getId(), r.getRequester().getFullName(), r.getRequester().getEmail(),
                    r.getPayer().getId(), r.getPayer().getFullName(), r.getPayer().getEmail(),
                    r.getAmount(), r.getCurrency(), r.getNote(), r.getStatus(),
                    r.getTransaction() == null ? null : r.getTransaction().getReference(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
