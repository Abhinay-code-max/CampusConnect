package com.campusconnect.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA256 password hashing with per-account random salt. */
public final class PasswordHasher {
    public static final int ITERATIONS = 600_000;
    public static final int SALT_BYTES = 16;
    public static final int HASH_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Hash(byte[] salt, byte[] value, int iterations) { }
    private PasswordHasher() { }

    public static Hash hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new Hash(salt, derive(password.toCharArray(), salt, ITERATIONS), ITERATIONS);
    }

    public static boolean verify(String password, byte[] salt, byte[] expected, int iterations) {
        if (password == null || salt == null || expected == null || salt.length != SALT_BYTES
                || expected.length != HASH_BYTES || iterations < ITERATIONS) return false;
        byte[] actual = derive(password.toCharArray(), salt, iterations);
        try { return MessageDigest.isEqual(actual, expected); }
        finally { Arrays.fill(actual, (byte) 0); }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BYTES * 8);
        Arrays.fill(password, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable", e);
        } finally { spec.clearPassword(); }
    }
}
