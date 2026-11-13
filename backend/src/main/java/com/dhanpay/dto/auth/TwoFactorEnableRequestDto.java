package com.dhanpay.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload to verify the first code and activate TOTP 2FA.
 */
public record TwoFactorEnableRequestDto(
        @NotBlank(message = "6-digit verification code is required")
        @Pattern(regexp = "^\\d{6}$", message = "Verification code must be exactly 6 digits")
        String code
) {
}
