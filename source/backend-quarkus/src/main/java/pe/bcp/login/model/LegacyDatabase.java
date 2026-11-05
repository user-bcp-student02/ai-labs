// LegacyDatabase.java
package pe.bcp.login.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.util.Objects;

public final class LegacyDatabase {
    private static final String FIND_BY_CREDENTIALS_SQL =
            "SELECT username FROM users WHERE username = ? AND password = ?";

    private final DataSource dataSource;

    public LegacyDatabase(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Busca un usuario usando parámetros SQL, nunca concatenando credenciales.
     * Devuelve null si las credenciales no coinciden.
     */
    public User findByCredentials(String username, String password) throws SQLException {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_CREDENTIALS_SQL)) {
            statement.setString(1, username);
            statement.setString(2, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new User(resultSet.getString("username"));
            }
        }
    }

    public static final class User {
        private final String username;

        public User(String username) {
            this.username = Objects.requireNonNull(username, "username");
        }

        public String getUsername() {
            return username;
        }
    }
}