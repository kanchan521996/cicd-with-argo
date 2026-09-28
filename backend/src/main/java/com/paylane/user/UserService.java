package com.paylane.user;

import com.paylane.common.ApiException;
import com.paylane.user.UserDtos.ChangePasswordRequest;
import com.paylane.user.UserDtos.RecipientResponse;
import com.paylane.user.UserDtos.SetPinRequest;
import com.paylane.user.UserDtos.UpdateProfileRequest;
import com.paylane.user.UserDtos.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public User getActive(Long id) {
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("ACCOUNT_FROZEN", "This account is frozen");
        }
        return user;
    }

    /** Finds a user by email or phone number. */
    public User findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw ApiException.badRequest("RECIPIENT_REQUIRED", "Enter an email or phone number");
        }
        String value = identifier.trim();
        return (UserIdentifiers.looksLikeEmail(value)
                ? users.findByEmail(UserIdentifiers.normalizeEmail(value))
                : users.findByPhone(UserIdentifiers.normalizePhone(value)))
                .orElseThrow(() -> ApiException.notFound("No Paylane user matches " + value));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long id) {
        return UserResponse.from(users.findById(id).orElseThrow(() -> ApiException.notFound("User not found")));
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest req) {
        User user = getActive(id);
        String phone = UserIdentifiers.normalizePhone(req.phone());
        if (!phone.equals(user.getPhone()) && users.existsByPhone(phone)) {
            throw ApiException.conflict("PHONE_TAKEN", "This phone number belongs to another account");
        }
        user.setFullName(req.fullName().trim());
        user.setPhone(phone);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest req) {
        User user = getActive(id);
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
    }

    @Transactional
    public UserResponse setPin(Long id, SetPinRequest req) {
        User user = getActive(id);
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_PASSWORD", "Password is incorrect");
        }
        user.setPinHash(encoder.encode(req.pin()));
        user.setPinAttempts(0);
        user.setPinLockedUntil(null);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public RecipientResponse lookup(Long requesterId, String identifier) {
        User user = findByIdentifier(identifier);
        if (user.getId().equals(requesterId)) {
            throw ApiException.badRequest("SELF_RECIPIENT", "That's your own account");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unprocessable("RECIPIENT_UNAVAILABLE", "This account can't receive payments right now");
        }
        return RecipientResponse.from(user);
    }
}
