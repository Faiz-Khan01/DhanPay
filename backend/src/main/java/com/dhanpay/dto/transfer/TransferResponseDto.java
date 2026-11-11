package com.dhanpay.dto.transfer;

import com.dhanpay.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safe transfer receipt returned to API clients.
 */
public record TransferResponseDto(
        String transferReference,
        String senderWalletReference,
        String recipientWalletReference,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        BigDecimal senderRemainingBalance,
        Instant createdAt
) {
}
