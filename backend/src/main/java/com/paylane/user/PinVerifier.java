package com.paylane.user;

import com.paylane.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Throws when the PIN check fails. Call it from controllers, outside any open
 * transaction: PinService commits failed attempts in its own transaction.
 */
@Component
public class PinVerifier {

    private final PinService pinService;

    public PinVerifier(PinService pinService) {
        this.pinService = pinService;
    }

    public void verify(Long userId, String pin) {
        ApiException error = pinService.check(userId, pin);
        if (error != null) {
            throw error;
        }
    }
}
