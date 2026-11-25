package com.nashi.util;

import java.security.SecureRandom;

public class StringUtils {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** URL slug: lower case, Latin and Cyrillic letters and digits, words joined by "-". */
    public static String toSlug(String input) {
        return input.toLowerCase()
                .replaceAll("[^a-z0-9\\u0400-\\u04FF]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(RANDOM.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}