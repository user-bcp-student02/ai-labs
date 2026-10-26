package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesWithUniqueSaltsAndVerifiesCredentials() {
        String firstHash = passwordHasher.hash("LocalPassword123!");
        String secondHash = passwordHasher.hash("LocalPassword123!");

        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.isHashed(firstHash));
        assertTrue(passwordHasher.matches("LocalPassword123!", firstHash));
        assertFalse(passwordHasher.matches("WrongPassword123!", firstHash));
    }

    @Test
    void rejectsMalformedStoredHashes() {
        assertFalse(passwordHasher.matches("password", "pbkdf2-sha256$600000$salt"));
        assertFalse(passwordHasher.matches("password", "pbkdf2-sha256$not-a-number$salt$hash"));
        assertFalse(passwordHasher.matches("password", "pbkdf2-sha256$600001$salt$hash"));
        assertFalse(passwordHasher.matches("password", "pbkdf2-sha256$1$%$hash"));
    }
}
