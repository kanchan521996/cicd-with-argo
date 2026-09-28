package com.paylane.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletResponse(
            Long id,
            BigDecimal balance,
            String currency,
            WalletStatus status,
            BigDecimal dailyLimit,
            BigDecimal spentToday,
            BigDecimal remainingToday
    ) {
    }

    public record TopUpRequest(
            @NotNull Long paymentMethodId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
    ) {
    }

    public record WithdrawRequest(
            @NotNull Long paymentMethodId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank String pin
    ) {
    }
}
