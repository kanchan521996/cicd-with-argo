package com.paylane.user;

public final class UserIdentifiers {

    private UserIdentifiers() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static String normalizePhone(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s\\-()]", "");
    }

    public static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@");
    }
}
