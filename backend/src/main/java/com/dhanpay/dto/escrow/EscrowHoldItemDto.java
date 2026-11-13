package com.dhanpay.dto.escrow;

import com.dhanpay.entity.EscrowStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * View DTO for an escrow hold visible to the buyer or seller.
 */
public record EscrowHoldItemDto(
        String reference,
        String role,
        String buyerWalletReference,
        String buyerName,
        String sellerWalletReference,
        String sellerName,
        BigDecimal amount,
        String currency,
        EscrowStatus status,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
