package com.dhanpay.dto.qr;

import java.math.BigDecimal;

/**
 * Branded QR payload and SVG representation for receiving funds.
 */
public record QrCodeResponseDto(
        String walletReference,
        String accountHolderName,
        String upiUri,
        String dhanpayUri,
        BigDecimal amount,
        String currency,
        String note,
        String qrSvg
) {
}
