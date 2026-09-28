package com.paylane.gateway;

import com.paylane.paymentmethod.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated processor with predictable test numbers:
 * card/account ending 0002 is declined, ending 9995 has insufficient funds,
 * everything else is approved.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public GatewayResult charge(PaymentMethod card, BigDecimal amount, String reference) {
        GatewayResult result = decide(card.getLast4(), "Card was declined by the issuer",
                "Card has insufficient funds");
        log.info("mock-gateway charge ref={} last4={} amount={} success={}", reference, card.getLast4(), amount,
                result.success());
        return result;
    }

    @Override
    public GatewayResult payout(PaymentMethod bankAccount, BigDecimal amount, String reference) {
        GatewayResult result = decide(bankAccount.getLast4(), "Bank rejected the transfer",
                "Bank rejected the transfer");
        log.info("mock-gateway payout ref={} last4={} amount={} success={}", reference, bankAccount.getLast4(),
                amount, result.success());
        return result;
    }

    private GatewayResult decide(String last4, String declined, String insufficient) {
        if ("0002".equals(last4)) {
            return GatewayResult.declined(declined);
        }
        if ("9995".equals(last4)) {
            return GatewayResult.declined(insufficient);
        }
        return GatewayResult.ok("mock_" + UUID.randomUUID());
    }
}
