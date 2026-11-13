package com.dhanpay.dto.developer;

import com.dhanpay.developer.WebhookStatus;

import java.time.Instant;

public record WebhookEndpointDto(
        Long id,
        String url,
        String secretKey,
        String description,
        WebhookStatus status,
        String events,
        Instant createdAt,
        Instant updatedAt
) {
}
