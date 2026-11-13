package com.dhanpay.dto.recurring;

import com.dhanpay.entity.RecurringFrequency;
import com.dhanpay.entity.RecurringStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * View DTO for an automated standing order rule.
 */
public record RecurringTransferItemDto(
        String reference,
        String role,
        String senderWalletReference,
        String senderName,
        String recipientWalletReference,
        String recipientName,
        BigDecimal amount,
        String currency,
        RecurringFrequency frequency,
        String description,
        RecurringStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Instant nextExecutionAt,
        Instant lastExecutedAt,
        int totalExecutions,
        Integer maxExecutions,
        int failureCount,
        String lastFailureReason,
        Instant createdAt
) {
}
