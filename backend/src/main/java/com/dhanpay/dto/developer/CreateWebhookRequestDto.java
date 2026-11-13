package com.dhanpay.dto.developer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWebhookRequestDto(
        @NotBlank(message = "Webhook URL is required")
        @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://")
        @Size(max = 512, message = "URL must not exceed 512 characters")
        String url,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        String events
) {
}
