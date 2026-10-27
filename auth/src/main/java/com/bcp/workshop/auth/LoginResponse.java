package com.bcp.workshop.auth;

public record LoginResponse(String token, int expiresIn, UserDetails user, String debug) {
    public record UserDetails(String username, String displayName, String role) {}
}
