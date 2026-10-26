package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class.getName());

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        Response validationResponse = validateRequest(request);
        if (validationResponse != null) {
            return validationResponse;
        }

        try {
            Optional<LoginResponse> loginResponse =
                    authService.authenticate(request.username, request.password);
            if (loginResponse.isEmpty()) {
                return Response.status(401)
                        .entity(Map.of("message", "Invalid user " + request.username + " or password"))
                        .build();
            }
            return Response.ok(loginResponse.get()).build();
        } catch (Exception error) {
            LOGGER.log(Level.SEVERE, "Authentication request failed", error);
            return Response.status(500)
                    .entity(Map.of("message", "Authentication failed"))
                    .build();
        }
    }

    private Response validateRequest(LoginRequest request) {
        if (request == null) {
            return badRequest("Request is null");
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return badRequest("Username is required");
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return badRequest("Password is required");
        }
        return null;
    }

    private Response badRequest(String message) {
        return Response.status(400).entity(Map.of("message", message)).build();
    }
}
