package com.dhanpay.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for requesting funds from another customer wallet.
 */
public record CreatePaymentRequestDto(
        @NotBlank(message = "Payer wallet reference is required")
        @Size(max = 64, message = "Wallet reference cannot exceed 64 characters")
        String payerWalletReference,

        @NotNull(message = "Request amount is required")
        @DecimalMin(value = "1.00", message = "Minimum request amount is ₹1.00")
        @DecimalMax(value = "50000.00", message = "Single payment request cannot exceed ₹50,000.00")
        @Digits(integer = 15, fraction = 4, message = "Amount supports up to 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Note cannot exceed 255 characters")
        String note
) {
}
