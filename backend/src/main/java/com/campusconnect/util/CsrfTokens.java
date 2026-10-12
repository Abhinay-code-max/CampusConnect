package com.campusconnect.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CsrfTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private CsrfTokens() { }
    public static String create() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public static boolean matches(String expected, String supplied) {
        return expected != null && supplied != null
                && MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
