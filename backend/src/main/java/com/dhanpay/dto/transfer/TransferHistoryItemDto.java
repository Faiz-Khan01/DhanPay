package com.dhanpay.dto.transfer;

import com.dhanpay.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Customer-centric transfer history entry including direction relative to the caller's wallet.
 */
public record TransferHistoryItemDto(
        String reference,
        String direction,
        String counterpartyWalletReference,
        String counterpartyName,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        String failureReason,
        Instant createdAt
) {
}
