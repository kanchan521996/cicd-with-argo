package com.paylane.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void normalizesToTwoDecimals() {
        assertEquals(new BigDecimal("10.50"), Money.normalize(new BigDecimal("10.5")));
        assertEquals(new BigDecimal("3.00"), Money.normalize(new BigDecimal("3.000")));
    }

    @Test
    void rejectsZeroNegativeAndFractionalCents() {
        assertThrows(ApiException.class, () -> Money.normalize(BigDecimal.ZERO));
        assertThrows(ApiException.class, () -> Money.normalize(new BigDecimal("-1")));
        assertThrows(ApiException.class, () -> Money.normalize(new BigDecimal("1.005")));
    }
}
