package com.dhanpay.dto.recurring;

import com.dhanpay.entity.RecurringFrequency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload to create an automated recurring transfer rule.
 */
public record CreateRecurringTransferRequestDto(
        @NotBlank(message = "Recipient wallet reference is required")
        @Size(max = 64, message = "Recipient wallet reference cannot exceed 64 characters")
        String recipientWalletReference,

        @NotNull(message = "Recurring amount is required")
        @DecimalMin(value = "1.00", message = "Minimum recurring amount is ₹1.00")
        @DecimalMax(value = "50000.00", message = "Maximum single recurring transfer is ₹50,000.00")
        @Digits(integer = 15, fraction = 4, message = "Amount supports up to 4 decimal places")
        BigDecimal amount,

        @NotNull(message = "Frequency is required (DAILY, WEEKLY, MONTHLY)")
        RecurringFrequency frequency,

        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        LocalDate startDate,
        LocalDate endDate,
        Integer maxExecutions
) {
}
