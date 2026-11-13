package com.dhanpay.dto.developer;

import com.dhanpay.developer.WebhookDeliveryStatus;

import java.time.Instant;

public record WebhookDeliveryDto(
        Long id,
        String eventType,
        String eventId,
        Integer httpStatus,
        WebhookDeliveryStatus status,
        String errorMessage,
        Instant dispatchedAt
) {
}
