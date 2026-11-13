package com.dhanpay.dto.statement;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementLineItemDto(
        Long id,
        Instant timestamp,
        String reference,
        String type,
        String counterpartyWalletReference,
        String counterpartyName,
        String description,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        BigDecimal balanceAfter
) {
}
