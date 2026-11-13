package com.dhanpay.dto.developer;

import com.dhanpay.developer.ApiKeyEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequestDto(
        @NotBlank(message = "API key label/name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        ApiKeyEnvironment environment,

        Integer expiryDays
) {
}
