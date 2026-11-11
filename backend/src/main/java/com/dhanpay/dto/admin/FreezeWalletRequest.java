package com.dhanpay.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for freezing a wallet with a mandatory audit reason.
 */
public record FreezeWalletRequest(
        @NotBlank(message = "A freeze reason is mandatory for audit logging")
        @Size(min = 3, max = 500, message = "Freeze reason must be between 3 and 500 characters")
        String reason
) {
}
