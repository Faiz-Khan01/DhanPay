package com.dhanpay.dto.auth;

import java.util.List;

/**
 * TOTP setup payload containing manual secret, standard otpauth URI, SVG QR code, and backup recovery codes.
 */
public record TwoFactorSetupResponseDto(
        String secret,
        String otpauthUri,
        String qrSvg,
        List<String> backupCodes
) {
}
