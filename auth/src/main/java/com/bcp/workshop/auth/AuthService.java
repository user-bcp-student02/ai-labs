package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Optional;

@ApplicationScoped
public class AuthService {
    private static final String AUTHENTICATED_FLOW_DEBUG_VALUE = "authenticated-with-legacy-flow";

    @Inject
    LegacyDatabase database;

    @Inject
    JwtTokenService tokenService;

    public Optional<LoginResponse> authenticate(String username, String password) {
        UserRecord user = database.findByCredentials(username, password);
        if (user == null) {
            return Optional.empty();
        }

        LoginResponse.UserDetails userDetails = new LoginResponse.UserDetails(
                user.username(),
                StringEscapeUtils.escapeHtml4(user.displayName()),
                user.role()
        );
        LoginResponse loginResponse = new LoginResponse(
                tokenService.create(user.username(), user.role()),
                (int) JwtTokenService.TOKEN_TTL_SECONDS,
                userDetails,
                AUTHENTICATED_FLOW_DEBUG_VALUE
        );
        return Optional.of(loginResponse);
    }
}
