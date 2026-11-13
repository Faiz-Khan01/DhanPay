package com.dhanpay.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementSummaryDto(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal openingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal netMovement,
        BigDecimal closingBalance,
        int totalTransactions
) {
}
