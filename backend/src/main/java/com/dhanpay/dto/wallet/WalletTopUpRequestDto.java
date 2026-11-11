package com.dhanpay.dto.wallet;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload for self-service INR wallet funding.
 */
public record WalletTopUpRequestDto(
        @NotNull(message = "Top-up amount is required")
        @DecimalMin(value = "1.00", message = "Minimum top-up amount is ₹1.00")
        @DecimalMax(value = "100000.00", message = "Maximum single top-up amount is ₹1,00,000.00")
        @Digits(integer = 15, fraction = 4, message = "Amount supports up to 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Source label cannot exceed 255 characters")
        String sourceLabel
) {
}
