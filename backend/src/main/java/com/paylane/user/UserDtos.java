package com.paylane.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public static final String PHONE_REGEX = "^\\+?[0-9]{8,15}$";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PIN_REGEX = "^[0-9]{4,6}$";

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "must be 8-15 digits, optional leading +") String phone,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX,
                    message = "must be 8-72 characters with at least one letter and one number") String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(
            Long id,
            String fullName,
            String email,
            String phone,
            Role role,
            UserStatus status,
            boolean pinSet,
            Instant createdAt
    ) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                    u.getStatus(), u.hasPin(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "must be 8-15 digits, optional leading +") String phone
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX,
                    message = "must be 8-72 characters with at least one letter and one number") String newPassword
    ) {
    }

    public record SetPinRequest(
            @NotBlank String password,
            @NotBlank @Pattern(regexp = PIN_REGEX, message = "must be 4 to 6 digits") String pin
    ) {
    }

    /** Minimal public view used to confirm a recipient before paying them. */
    public record RecipientResponse(Long id, String fullName, String maskedEmail, String maskedPhone) {
        public static RecipientResponse from(User u) {
            return new RecipientResponse(u.getId(), u.getFullName(), maskEmail(u.getEmail()), maskPhone(u.getPhone()));
        }
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }

    static String maskPhone(String phone) {
        if (phone.length() <= 4) {
            return phone;
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}
