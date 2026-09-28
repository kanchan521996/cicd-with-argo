package com.paylane.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    /** Validates a user-supplied amount: positive, max 2 decimals, returned with scale 2. */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount is required");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount can have at most 2 decimal places");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        return value;
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
