package cn.minelock.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static PasswordHash hash(String password, int iterations) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations);
        return new PasswordHash(
                iterations,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

    public static boolean verify(String password, int iterations, String saltBase64, String hashBase64) {
        if (saltBase64 == null || hashBase64 == null || iterations <= 0) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(saltBase64);
            expected = Base64.getDecoder().decode(hashBase64);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        byte[] actual = pbkdf2(password, salt, iterations);
        return constantTimeEquals(actual, expected);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null || right == null) {
            return false;
        }
        int diff = left.length ^ right.length;
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            byte a = i < left.length ? left[i] : 0;
            byte b = i < right.length ? right[i] : 0;
            diff |= a ^ b;
        }
        return diff == 0;
    }

    public record PasswordHash(int iterations, String salt, String hash) {
    }
}
