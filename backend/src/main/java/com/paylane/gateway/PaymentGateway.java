package com.paylane.gateway;

import com.paylane.paymentmethod.PaymentMethod;
import java.math.BigDecimal;

/**
 * Boundary to the outside payment network. Swap MockPaymentGateway for a real
 * provider (Stripe, Adyen, ...) without touching the wallet logic.
 */
public interface PaymentGateway {

    /** Pull money from a card into the platform. */
    GatewayResult charge(PaymentMethod card, BigDecimal amount, String reference);

    /** Push money from the platform out to a bank account. */
    GatewayResult payout(PaymentMethod bankAccount, BigDecimal amount, String reference);
}
