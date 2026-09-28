package com.paylane.paymentmethod;

public final class CardValidator {

    private CardValidator() {
    }

    /** Luhn checksum used by all major card networks. */
    public static boolean luhn(String digits) {
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    public static String brand(String digits) {
        if (digits.startsWith("4")) {
            return "Visa";
        }
        if (digits.matches("^(5[1-5]|2[2-7]).*")) {
            return "Mastercard";
        }
        if (digits.matches("^3[47].*")) {
            return "Amex";
        }
        if (digits.matches("^(6011|65|64[4-9]).*")) {
            return "Discover";
        }
        if (digits.matches("^(60|65|81|82|508).*")) {
            return "RuPay";
        }
        return "Card";
    }
}
