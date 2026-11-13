package com.dhanpay.dto.developer;

public record WebhookTestResponseDto(
        boolean success,
        Integer httpStatus,
        String signature,
        String payload,
        String errorMessage
) {
}
