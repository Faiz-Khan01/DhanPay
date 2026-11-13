package com.dhanpay.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for reversing a settled P2P transfer with a mandatory audit reason.
 */
public record ReverseTransferRequestDto(
        @NotBlank(message = "A reversal reason is mandatory for audit logging")
        @Size(min = 3, max = 500, message = "Reversal reason must be between 3 and 500 characters")
        String reason
) {
}
