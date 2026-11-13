package com.dhanpay.dto.auth;

/**
 * Current status of two-factor authentication for the authenticated user.
 */
public record TwoFactorStatusDto(
        boolean enabled,
        int remainingBackupCodes
) {
}
