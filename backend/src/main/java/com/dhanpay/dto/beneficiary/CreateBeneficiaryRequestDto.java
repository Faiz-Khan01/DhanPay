package com.dhanpay.dto.beneficiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to save a recipient wallet as a named beneficiary.
 */
public record CreateBeneficiaryRequestDto(
        @NotBlank(message = "Recipient wallet reference is required")
        @Size(max = 64, message = "Wallet reference cannot exceed 64 characters")
        String walletReference,

        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 80, message = "Nickname must be between 2 and 80 characters")
        String nickname
) {
}
