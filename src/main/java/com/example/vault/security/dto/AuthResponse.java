package com.example.vault.security.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs
) {
}
