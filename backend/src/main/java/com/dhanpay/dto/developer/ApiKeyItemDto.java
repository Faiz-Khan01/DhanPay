package com.dhanpay.dto.developer;

import com.dhanpay.developer.ApiKeyEnvironment;
import com.dhanpay.developer.ApiKeyStatus;

import java.time.Instant;

public record ApiKeyItemDto(
        Long id,
        String name,
        String keyPrefix,
        ApiKeyEnvironment environment,
        ApiKeyStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant lastUsedAt
) {
}
