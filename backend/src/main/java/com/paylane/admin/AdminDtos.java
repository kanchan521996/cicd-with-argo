package com.paylane.admin;

import com.paylane.user.Role;
import com.paylane.user.UserStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record StatsResponse(
            long totalUsers,
            long frozenUsers,
            BigDecimal totalWalletBalance,
            long transactionsToday,
            long failedToday,
            BigDecimal volumeToday,
            BigDecimal volumeThisMonth,
            String currency
    ) {
    }

    public record AdminUserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            Role role,
            UserStatus status,
            BigDecimal balance,
            BigDecimal dailyLimit,
            Instant createdAt
    ) {
    }

    public record UpdateStatusRequest(@NotNull UserStatus status) {
    }

    public record UpdateLimitRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal dailyLimit
    ) {
    }

    public record ReverseRequest(@NotBlank @Size(max = 140) String reason) {
    }
}
