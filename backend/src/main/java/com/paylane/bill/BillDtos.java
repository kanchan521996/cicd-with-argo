package com.paylane.bill;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public final class BillDtos {

    private BillDtos() {
    }

    public record BillerResponse(Long id, String code, String name, String category, String accountLabel) {
        public static BillerResponse from(Biller b) {
            return new BillerResponse(b.getId(), b.getCode(), b.getName(), b.getCategory(), b.getAccountLabel());
        }
    }

    public record PayBillRequest(
            @NotNull Long billerId,
            @NotBlank @Pattern(regexp = "^[0-9A-Za-z\\-+]{4,40}$", message = "must be 4-40 letters, digits or dashes")
            String accountReference,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank String pin
    ) {
    }
}
