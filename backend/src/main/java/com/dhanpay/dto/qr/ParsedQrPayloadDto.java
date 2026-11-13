package com.dhanpay.dto.qr;

import java.math.BigDecimal;

/**
 * Verified counterparty details extracted from a scanned QR payload.
 */
public record ParsedQrPayloadDto(
        String rawPayload,
        String targetWalletReference,
        String targetFullName,
        BigDecimal amount,
        String currency,
        String description,
        boolean targetActive,
        boolean self
) {
}
