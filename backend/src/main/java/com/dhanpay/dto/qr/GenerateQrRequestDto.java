package com.dhanpay.dto.qr;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Optional parameters to embed a fixed amount or note into a dynamic payment QR code.
 */
public record GenerateQrRequestDto(
        @DecimalMin(value = "1.00", message = "Minimum QR amount is ₹1.00")
        @DecimalMax(value = "50000.00", message = "Maximum single transfer QR amount is ₹50,000.00")
        @Digits(integer = 15, fraction = 4, message = "Amount supports up to 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note
) {
}
