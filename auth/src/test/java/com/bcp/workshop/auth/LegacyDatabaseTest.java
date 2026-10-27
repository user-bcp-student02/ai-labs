package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    private static final String DEFAULT_TRAINER_PASSWORD = "Trainer123!";
    private static final String CUSTOM_TRAINER_PASSWORD = "DeploymentSpecificTrainerPassword!";

    @Test
    void doesNotSeedOrRetainDefaultCredentialsWhenSeedingIsDisabled() throws Exception {
        LegacyDatabase database = new LegacyDatabase();
        database.passwordHasher = new PasswordHasher();
        Class.forName("org.sqlite.JDBC");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            database.seedDefaultUsers = false;
            database.initializeDatabase(connection);
            assertEquals(0, countUsers(connection));

            database.seedDefaultUsers = true;
            database.initializeDatabase(connection);
            assertEquals(2, countUsers(connection));

            updatePassword(connection, "student", "Workshop123!");
            updatePassword(connection, "trainer", database.passwordHasher.hash(CUSTOM_TRAINER_PASSWORD));

            database.seedDefaultUsers = false;
            database.initializeDatabase(connection);

            assertNull(readPassword(connection, "student"));
            assertTrue(database.passwordHasher.matches(
                    CUSTOM_TRAINER_PASSWORD, readPassword(connection, "trainer")));

            updatePassword(connection, "trainer", database.passwordHasher.hash(DEFAULT_TRAINER_PASSWORD));
            database.initializeDatabase(connection);
            assertNull(readPassword(connection, "trainer"));
            assertEquals(0, countUsers(connection));
        }
    }

    private static int countUsers(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM legacy_users");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String readPassword(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("password") : null;
            }
        }
    }

    private static void updatePassword(Connection connection, String username, String password)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE legacy_users SET password=? WHERE username=?")) {
            statement.setString(1, password);
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }
}
