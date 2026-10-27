package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String HASH_PREFIX = "pbkdf2-sha256";
    private static final String KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATION_COUNT);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return HASH_PREFIX + "$" + ITERATION_COUNT + "$"
                + encoder.encodeToString(salt) + "$"
                + encoder.encodeToString(derivedKey);
    }

    public boolean matches(String password, String storedValue) {
        if (!isHashed(storedValue)) {
            return MessageDigest.isEqual(
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    storedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        String[] hashParts = storedValue.split("\\$", -1);
        if (hashParts.length != 4) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(hashParts[1]);
            if (iterations <= 0 || iterations > ITERATION_COUNT) {
                return false;
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(hashParts[2]);
            byte[] expectedHash = decoder.decode(hashParts[3]);
            byte[] actualHash = deriveKey(password, salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public boolean isHashed(String storedValue) {
        return storedValue != null && storedValue.startsWith(HASH_PREFIX + "$");
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                HASH_LENGTH_BITS
        );
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to hash password", error);
        } finally {
            keySpec.clearPassword();
        }
    }
}
