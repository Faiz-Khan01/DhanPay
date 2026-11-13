package com.dhanpay.dto.qr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload submitted when scanning a QR code (UPI URI, DhanPay URI, or raw wallet reference).
 */
public record ParseQrRequestDto(
        @NotBlank(message = "Scanned QR payload is required")
        @Size(max = 2048, message = "Payload exceeds maximum allowed length")
        String payload
) {
}
