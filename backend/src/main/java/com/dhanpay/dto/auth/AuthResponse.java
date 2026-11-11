package com.dhanpay.dto.auth;

/**
 * Authentication response containing the signed JWT access token and user metadata.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserSummaryResponse user
) {
}
