package com.dhanpay.dto.wallet;

import com.dhanpay.entity.WalletStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Authenticated customer's primary wallet summary projection.
 */
public record WalletSummaryDto(
        String walletReference,
        BigDecimal balance,
        String currency,
        WalletStatus status,
        String ledgerAccountNumber,
        Instant updatedAt
) {
}
