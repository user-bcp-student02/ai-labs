package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String TEST_JWT_SECRET = "auth-test-secret-for-hs256-32-bytes";
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DATABASE_URL = System.getenv()
            .getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @InjectSpy
    AuthService authService;

    @jakarta.inject.Inject
    JwtTokenService tokenService;

    @Test
    void reportsHealthAsUp() {
        when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks.size()", is(0));
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithDefaultTrainerAccount() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"trainer\",\"password\":\"Trainer123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.role", is("TRAINER"));
    }

    @Test
    void issuesSignedJwtWithLoginClaimsAndExpiry() throws Exception {
        String token = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        String[] segments = token.split("\\.", -1);
        assertEquals(3, segments.length);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode header = objectMapper.readTree(URL_DECODER.decode(segments[0]));
        JsonNode claims = objectMapper.readTree(URL_DECODER.decode(segments[1]));
        assertEquals("HS256", header.path("alg").asText());
        assertEquals("student", claims.path("username").asText());
        assertEquals("STUDENT", claims.path("role").asText());
        assertEquals("student", claims.path("sub").asText());
        assertTrue(claims.path("exp").asLong() > Instant.now().getEpochSecond());
        assertEquals(3600, claims.path("exp").asLong() - claims.path("iat").asLong());

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] expectedSignature = mac.doFinal((segments[0] + "." + segments[1])
                .getBytes(StandardCharsets.US_ASCII));
        assertTrue(java.security.MessageDigest.isEqual(
                expectedSignature, URL_DECODER.decode(segments[2])));
        assertTrue(tokenService.isValidToken(token));
    }

    @Test
    void rejectsAlteredAndExpiredJwtTokens() throws Exception {
        String validToken = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
        String[] segments = validToken.split("\\.", -1);
        String alteredToken = segments[0] + "." + segments[1] + "." + URL_ENCODER.encodeToString(
                new byte[]{1, 2, 3});

        long expiredAt = Instant.now().getEpochSecond() - 1;
        String expiredToken = createTestToken("student", "STUDENT", expiredAt - 3600, expiredAt);

        assertFalse(tokenService.isValidToken(alteredToken));
        assertFalse(tokenService.isValidToken(expiredToken));
    }

    @Test
    void rejectsMalformedTokensAndWeakSigningSecrets() throws Exception {
        String unsupportedAlgorithmToken = createTestToken(
                "student", "STUDENT", Instant.now().getEpochSecond(),
                Instant.now().getEpochSecond() + 3600, "HS384");

        assertFalse(tokenService.isValidToken(null));
        assertFalse(tokenService.isValidToken("not.a.valid.jwt"));
        assertFalse(tokenService.isValidToken("!!.!!.!!"));
        assertFalse(tokenService.isValidToken(unsupportedAlgorithmToken));

        JwtTokenService invalidConfiguration = new JwtTokenService();
        invalidConfiguration.signingSecret = "short";
        assertThrows(IllegalStateException.class, invalidConfiguration::validateConfiguration);
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Incorrect123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\" \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullLoginRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMalformedJsonAtTransportBoundary() {
        String responseBody = given()
                .contentType("application/json")
                .body("{")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .extract()
                .asString();

        assertEquals("", responseBody);
    }

    @Test
    void rejectsUnsupportedContentTypeAtTransportBoundary() {
        String responseBody = given()
                .contentType("text/plain")
                .body("plain text payload")
                .when().post("/auth/login")
                .then()
                .statusCode(415)
                .extract()
                .asString();

        assertEquals("", responseBody);
    }

    @Test
    void reportsGenericInternalErrorOverHttp() {
        org.mockito.Mockito.doThrow(new IllegalStateException("local database failure"))
                .when(authService).authenticate("student", "Workshop123!");

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("Authentication failed"))
                .body("exception", org.hamcrest.Matchers.nullValue());
    }

    @Test
    void treatsQuotedUsernameAsLiteralCredential() throws SQLException {
        String username = "test-" + UUID.randomUUID() + "'user";
        String password = "LocalTest123!";
        insertTestUser(username, password);

        try {
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .body("user.username", is(username))
                    .body("user.displayName", is("Local &lt;Test&gt; &amp; User"))
                    .body("token", notNullValue());
        } finally {
            deleteTestUser(username);
        }
    }

    @Test
    void migratesLegacyPlaintextPasswordAfterSuccessfulLogin() throws SQLException {
        String username = "legacy-" + UUID.randomUUID();
        String password = "LocalLegacy123!";
        insertTestUser(username, password);

        try {
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200);

            String storedPassword = readStoredPassword(username);
            assertNotEquals(password, storedPassword);
            assertTrue(storedPassword.startsWith("pbkdf2-sha256$"));
        } finally {
            deleteTestUser(username);
        }
    }

    @Test
    void storesSeededPasswordsAsHashes() throws SQLException {
        String storedPassword = readStoredPassword("student");

        assertNotEquals("Workshop123!", storedPassword);
        assertTrue(storedPassword.startsWith("pbkdf2-sha256$"));
    }

    @Test
    void keepsSeededHashWhenDatabaseIsInitializedAgain() throws SQLException {
        String storedPassword = readStoredPassword("student");
        LegacyDatabase database = new LegacyDatabase();
        database.passwordHasher = new PasswordHasher();
        database.seedDefaultUsers = true;

        database.initialize();

        assertEquals(storedPassword, readStoredPassword("student"));
    }

    @Test
    void returnsGenericErrorWhenAuthenticationFails() {
        String internalMessage = "local database failure";
        AuthResource resource = new AuthResource();
        resource.authService = new AuthService() {
            @Override
            public Optional<LoginResponse> authenticate(String username, String password) {
                throw new IllegalStateException(internalMessage);
            }
        };

        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        Response response = resource.login(request);
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("Authentication failed", body.get("message"));
        assertFalse(body.containsKey("exception"));
        assertFalse(body.toString().contains(internalMessage));
    }

    private static void insertTestUser(String username, String password) throws SQLException {
        String sql = "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
        try (Connection connection = DriverManager.getConnection(DATABASE_URL)) {
            try (Statement schemaStatement = connection.createStatement()) {
                schemaStatement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)"
                );
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                statement.setString(2, password);
                statement.setString(3, "Local <Test> & User");
                statement.setString(4, "STUDENT");
                statement.executeUpdate();
            }
        }
    }

    private static void deleteTestUser(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    private static String readStoredPassword(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DATABASE_URL);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password FROM legacy_users WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Test user not found");
                }
                return result.getString("password");
            }
        }
    }

    private static String createTestToken(String username, String role, long issuedAt, long expiresAt)
            throws Exception {
        return createTestToken(username, role, issuedAt, expiresAt, "HS256");
    }

    private static String createTestToken(
            String username, String role, long issuedAt, long expiresAt, String algorithm) throws Exception {
        String header = URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(
                Map.of("alg", algorithm, "typ", "JWT")));
        String payload = URL_ENCODER.encodeToString(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "username", username,
                "sub", username,
                "role", role,
                "iat", issuedAt,
                "exp", expiresAt
        )));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = URL_ENCODER.encodeToString(
                mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + signature;
    }
}
