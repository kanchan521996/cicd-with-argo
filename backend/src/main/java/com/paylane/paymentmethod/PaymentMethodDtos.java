package com.paylane.paymentmethod;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PaymentMethodDtos {

    private PaymentMethodDtos() {
    }

    public record AddCardRequest(
            @NotBlank @Pattern(regexp = "^[0-9 ]{12,23}$", message = "must be 12-19 digits") String cardNumber,
            @NotBlank @Size(max = 120) String holderName,
            @NotNull @Min(1) @Max(12) Integer expiryMonth,
            @NotNull @Min(2000) @Max(2100) Integer expiryYear,
            @NotBlank @Pattern(regexp = "^[0-9]{3,4}$", message = "must be 3 or 4 digits") String cvv
    ) {
    }

    public record AddBankRequest(
            @NotBlank @Size(max = 120) String bankName,
            @NotBlank @Size(max = 120) String holderName,
            @NotBlank @Pattern(regexp = "^[0-9]{6,17}$", message = "must be 6-17 digits") String accountNumber,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{6,11}$", message = "must be 6-11 letters or digits") String routingCode
    ) {
    }

    public record PaymentMethodResponse(
            Long id,
            PaymentMethodType type,
            String brand,
            String last4,
            String label,
            String holderName,
            Integer expiryMonth,
            Integer expiryYear,
            String bankName,
            boolean primary,
            Instant createdAt
    ) {
        public static PaymentMethodResponse from(PaymentMethod pm) {
            return new PaymentMethodResponse(pm.getId(), pm.getType(), pm.getBrand(), pm.getLast4(), pm.label(),
                    pm.getHolderName(), pm.getExpiryMonth(), pm.getExpiryYear(), pm.getBankName(),
                    pm.isDefaultMethod(), pm.getCreatedAt());
        }
    }
}
