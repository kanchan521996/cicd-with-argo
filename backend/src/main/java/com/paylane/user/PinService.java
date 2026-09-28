package com.paylane.user;

import com.paylane.common.ApiException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the transaction PIN. Runs in its own transaction so failed attempts
 * are saved even when the calling payment rolls back.
 */
@Service
public class PinService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public PinService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Returns null when the PIN is correct, otherwise the error to throw. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApiException check(Long userId, String pin) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!user.hasPin()) {
            return ApiException.badRequest("PIN_NOT_SET", "Set a transaction PIN in Security before paying");
        }
        Instant now = Instant.now();
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(now)) {
            return new ApiException(HttpStatus.LOCKED, "PIN_LOCKED",
                    "Too many wrong PIN attempts. Try again after " + user.getPinLockedUntil());
        }
        if (pin != null && encoder.matches(pin, user.getPinHash())) {
            if (user.getPinAttempts() != 0 || user.getPinLockedUntil() != null) {
                user.setPinAttempts(0);
                user.setPinLockedUntil(null);
            }
            return null;
        }
        int attempts = user.getPinAttempts() + 1;
        if (attempts >= MAX_ATTEMPTS) {
            user.setPinAttempts(0);
            user.setPinLockedUntil(now.plus(LOCK_DURATION));
            return new ApiException(HttpStatus.LOCKED, "PIN_LOCKED",
                    "Too many wrong PIN attempts. Payments are locked for 15 minutes.");
        }
        user.setPinAttempts(attempts);
        return ApiException.badRequest("INVALID_PIN",
                "Wrong PIN. " + (MAX_ATTEMPTS - attempts) + " attempt(s) left.");
    }
}
