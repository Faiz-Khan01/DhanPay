package com.dhanpay.dto.developer;

import com.dhanpay.developer.ApiKeyEnvironment;

import java.time.Instant;

public record ApiKeyCreatedResponseDto(
        Long id,
        String name,
        String secretKey,
        String keyPrefix,
        ApiKeyEnvironment environment,
        Instant createdAt,
        Instant expiresAt,
        String warning
) {
}
