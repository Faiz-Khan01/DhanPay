package com.dhanpay.dto.admin;

import com.dhanpay.entity.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Administrative view of a P2P wallet transfer with reversal controls.
 */
public record AdminTransferItemDto(
        Long id,
        String reference,
        String senderWalletReference,
        String senderName,
        String recipientWalletReference,
        String recipientName,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
