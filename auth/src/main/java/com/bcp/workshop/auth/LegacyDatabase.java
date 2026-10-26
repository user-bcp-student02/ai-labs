package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String FIND_USER_BY_USERNAME_SQL =
            "SELECT id, username, password, display_name, role FROM legacy_users WHERE username=?";
    private static final String UPDATE_PASSWORD_SQL =
            "UPDATE legacy_users SET password=? WHERE id=? AND password=?";
    private static final String FIND_USER_ID_SQL =
            "SELECT id FROM legacy_users WHERE username=?";
    private static final String FIND_USER_PASSWORD_SQL =
            "SELECT id, password FROM legacy_users WHERE username=?";
    private static final String INSERT_SEED_USER_SQL =
            "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
    private static final String DELETE_USER_BY_ID_AND_PASSWORD_SQL =
            "DELETE FROM legacy_users WHERE id=? AND password=?";
    private static final List<SeedUser> DEFAULT_USERS = List.of(
            new SeedUser("student", "Workshop123!", "Workshop Student", "STUDENT"),
            new SeedUser("trainer", "Trainer123!", "Workshop Trainer", "TRAINER")
    );

    @Inject
    PasswordHasher passwordHasher;

    @ConfigProperty(name = "auth.database.seed-default-users", defaultValue = "false")
    boolean seedDefaultUsers;

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                initializeDatabase(connection);
            }
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("Unable to initialize authentication database", error);
        }
    }

    void initializeDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
        if (seedDefaultUsers) {
            seedDefaultUsers(connection);
            return;
        }
        removeUnchangedDefaultUsers(connection);
    }

    public UserRecord findByCredentials(String username, String password) {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String storedPassword;
            UserRecord user;
            try (PreparedStatement statement = connection.prepareStatement(FIND_USER_BY_USERNAME_SQL)) {
                statement.setString(1, username);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    storedPassword = result.getString("password");
                    user = new UserRecord(
                            result.getLong("id"),
                            result.getString("username"),
                            result.getString("display_name"),
                            result.getString("role")
                    );
                }
            }

            if (!passwordHasher.matches(password, storedPassword)) {
                return null;
            }
            if (!passwordHasher.isHashed(storedPassword)) {
                upgradeLegacyPassword(connection, user.id(), storedPassword, password);
            }
            return user;
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to look up user credentials", error);
        }
    }

    private void seedDefaultUsers(Connection connection) throws SQLException {
        for (SeedUser user : DEFAULT_USERS) {
            if (userExists(connection, user.username())) {
                continue;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SEED_USER_SQL)) {
                statement.setString(1, user.username());
                statement.setString(2, passwordHasher.hash(user.password()));
                statement.setString(3, user.displayName());
                statement.setString(4, user.role());
                statement.executeUpdate();
            }
        }
    }

    private void removeUnchangedDefaultUsers(Connection connection) throws SQLException {
        for (SeedUser user : DEFAULT_USERS) {
            removeDefaultUserIfPasswordUnchanged(connection, user);
        }
    }

    private void removeDefaultUserIfPasswordUnchanged(Connection connection, SeedUser user)
            throws SQLException {
        long userId;
        String storedPassword;
        try (PreparedStatement statement = connection.prepareStatement(FIND_USER_PASSWORD_SQL)) {
            statement.setString(1, user.username());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return;
                }
                userId = result.getLong("id");
                storedPassword = result.getString("password");
            }
        }
        if (passwordHasher.matches(user.password(), storedPassword)) {
            deleteUser(connection, userId, storedPassword);
        }
    }

    private void deleteUser(Connection connection, long userId, String storedPassword)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_USER_BY_ID_AND_PASSWORD_SQL)) {
            statement.setLong(1, userId);
            statement.setString(2, storedPassword);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_USER_ID_SQL)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void upgradeLegacyPassword(
            Connection connection,
            long userId,
            String legacyPassword,
            String submittedPassword
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD_SQL)) {
            statement.setString(1, passwordHasher.hash(submittedPassword));
            statement.setLong(2, userId);
            statement.setString(3, legacyPassword);
            statement.executeUpdate();
        }
    }

    private record SeedUser(String username, String password, String displayName, String role) {}
}
