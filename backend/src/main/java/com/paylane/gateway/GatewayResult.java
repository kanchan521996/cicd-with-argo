package com.paylane.gateway;

public record GatewayResult(boolean success, String providerReference, String message) {

    public static GatewayResult ok(String providerReference) {
        return new GatewayResult(true, providerReference, "Approved");
    }

    public static GatewayResult declined(String message) {
        return new GatewayResult(false, null, message);
    }
}
