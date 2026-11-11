package com.dhanpay.dto.request;

import com.dhanpay.entity.PaymentRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection of an incoming or outgoing payment request.
 */
public record PaymentRequestItemDto(
        String reference,
        String direction,
        String counterpartyName,
        String counterpartyWalletReference,
        BigDecimal amount,
        String currency,
        PaymentRequestStatus status,
        String note,
        String settledTransferReference,
        Instant createdAt
) {
}
