package com.dhanpay.dto.wallet;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response payload returned after an idempotent double-entry wallet top-up.
 */
public record WalletTopUpResponseDto(
        String topUpReference,
        String walletReference,
        String ledgerReference,
        BigDecimal amount,
        String currency,
        BigDecimal newBalance,
        String sourceLabel,
        Instant createdAt
) {
}
