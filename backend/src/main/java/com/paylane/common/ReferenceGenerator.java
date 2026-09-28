package com.paylane.common;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class ReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private ReferenceGenerator() {
    }

    /** e.g. PL20260925K7QX2M9A */
    public static String next() {
        StringBuilder sb = new StringBuilder("PL").append(LocalDate.now(ZoneOffset.UTC).format(DATE));
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
