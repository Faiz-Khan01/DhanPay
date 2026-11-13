package com.dhanpay.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to disable two-factor authentication requiring password and current TOTP or backup code.
 */
public record TwoFactorDisableRequestDto(
        @NotBlank(message = "Current account password is required")
        String password,

        @NotBlank(message = "Current 2FA code or backup code is required")
        String code
) {
}
