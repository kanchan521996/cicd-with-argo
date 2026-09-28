package com.paylane.user;

import com.paylane.common.ApiException;
import com.paylane.security.JwtService;
import com.paylane.user.UserDtos.AuthResponse;
import com.paylane.user.UserDtos.LoginRequest;
import com.paylane.user.UserDtos.RegisterRequest;
import com.paylane.user.UserDtos.UserResponse;
import com.paylane.wallet.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final WalletService walletService;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, WalletService walletService, PasswordEncoder encoder,
                       JwtService jwtService) {
        this.users = users;
        this.walletService = walletService;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = UserIdentifiers.normalizeEmail(req.email());
        String phone = UserIdentifiers.normalizePhone(req.phone());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (users.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "An account with this phone number already exists");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(Role.USER);
        users.save(user);
        walletService.createWalletFor(user);
        return toAuth(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(UserIdentifiers.normalizeEmail(req.email()))
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email or password is incorrect"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("ACCOUNT_FROZEN", "This account is frozen. Contact support.");
        }
        return toAuth(user);
    }

    private AuthResponse toAuth(User user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.getExpirationSeconds(),
                UserResponse.from(user));
    }
}
