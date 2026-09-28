package com.paylane.paymentmethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CardValidatorTest {

    @Test
    void acceptsValidTestCards() {
        assertTrue(CardValidator.luhn("4242424242424242"));
        assertTrue(CardValidator.luhn("4000000000000002"));
        assertTrue(CardValidator.luhn("5555555555554444"));
    }

    @Test
    void rejectsBadChecksum() {
        assertFalse(CardValidator.luhn("4242424242424241"));
    }

    @Test
    void detectsBrand() {
        assertEquals("Visa", CardValidator.brand("4242424242424242"));
        assertEquals("Mastercard", CardValidator.brand("5555555555554444"));
        assertEquals("Amex", CardValidator.brand("378282246310005"));
    }
}
