package com.paylane.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        JwtProps jwt,
        CorsProps cors,
        WalletProps wallet,
        AdminProps admin
) {
    public record JwtProps(String secret, long expirationMinutes) {
    }

    public record CorsProps(List<String> allowedOrigins) {
    }

    public record WalletProps(String currency, BigDecimal defaultDailyLimit, BigDecimal maxTransactionAmount) {
    }

    public record AdminProps(String email, String password, String fullName) {
    }
}
