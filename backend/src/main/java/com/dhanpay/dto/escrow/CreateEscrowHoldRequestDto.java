package com.dhanpay.dto.escrow;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request payload to lock funds in escrow for a seller wallet.
 */
public record CreateEscrowHoldRequestDto(
        @NotBlank(message = "Seller wallet reference is required")
        @Size(max = 64, message = "Seller wallet reference cannot exceed 64 characters")
        String sellerWalletReference,

        @NotNull(message = "Escrow amount is required")
        @DecimalMin(value = "1.00", message = "Minimum escrow hold is ₹1.00")
        @DecimalMax(value = "500000.00", message = "Maximum single escrow hold is ₹5,00,000.00")
        @Digits(integer = 15, fraction = 4, message = "Amount supports up to 4 decimal places")
        BigDecimal amount,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {
}
