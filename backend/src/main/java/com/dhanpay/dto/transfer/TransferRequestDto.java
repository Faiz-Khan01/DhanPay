package com.dhanpay.dto.transfer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for initiating a peer-to-peer wallet transfer.
 */
public record TransferRequestDto(
        @NotBlank(message = "Recipient wallet reference is required")
        @Size(max = 64, message = "Recipient wallet reference must not exceed 64 characters")
        String recipientWalletReference,

        @NotNull(message = "Transfer amount is required")
        @DecimalMin(value = "1.0000", message = "Transfer amount must be at least ₹1.0000")
        @DecimalMax(value = "50000.0000", message = "Transfer amount cannot exceed ₹50,000.0000")
        @Digits(integer = 15, fraction = 4, message = "Amount can have at most 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description
) {
}
