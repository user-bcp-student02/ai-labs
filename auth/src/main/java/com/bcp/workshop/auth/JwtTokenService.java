package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@ApplicationScoped
public class JwtTokenService {
    public static final long TOKEN_TTL_SECONDS = 3600;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final int JWT_SEGMENT_COUNT = 3;
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    @ConfigProperty(name = "auth.jwt.secret")
    String signingSecret;

    @Inject
    ObjectMapper objectMapper;

    @PostConstruct
    void validateConfiguration() {
        if (signingSecret == null
                || signingSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("auth.jwt.secret must contain at least 32 bytes");
        }
    }

    public String create(String username, String role) {
        long issuedAt = Instant.now().getEpochSecond();
        String header = encodeJson(Map.of("alg", JWT_ALGORITHM, "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "username", username,
                "sub", username,
                "role", role,
                "iat", issuedAt,
                "exp", issuedAt + TOKEN_TTL_SECONDS
        ));
        String signingInput = header + "." + payload;
        try {
            return signingInput + "." + URL_ENCODER.encodeToString(sign(signingInput));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    public boolean isValidToken(String token) {
        if (token == null) {
            return false;
        }

        String[] segments = token.split("\\.", -1);
        if (segments.length != JWT_SEGMENT_COUNT) {
            return false;
        }

        try {
            JsonNode header = objectMapper.readTree(URL_DECODER.decode(segments[0]));
            if (!JWT_ALGORITHM.equals(header.path("alg").asText())) {
                return false;
            }

            String signingInput = segments[0] + "." + segments[1];
            byte[] expectedSignature = sign(signingInput);
            byte[] actualSignature = URL_DECODER.decode(segments[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return false;
            }

            JsonNode claims = objectMapper.readTree(URL_DECODER.decode(segments[1]));
            return claims.path("username").isTextual()
                    && !claims.path("username").asText().isBlank()
                    && claims.path("role").isTextual()
                    && !claims.path("role").asText().isBlank()
                    && claims.path("exp").canConvertToLong()
                    && claims.path("exp").asLong() > Instant.now().getEpochSecond();
        } catch (IllegalArgumentException | IOException | GeneralSecurityException exception) {
            return false;
        }
    }

    private String encodeJson(Map<String, ?> value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize JWT", exception);
        }
    }

    private byte[] sign(String signingInput) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
    }
}
