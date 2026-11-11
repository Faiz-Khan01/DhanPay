package com.dhanpay.dto.transfer;

import com.dhanpay.ledger.EntryDirection;
import com.dhanpay.ledger.LedgerAccountType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single immutable debit or credit line inside a double-entry ledger transaction.
 */
public record JournalLineDto(
        String accountNumber,
        LedgerAccountType accountType,
        String walletReference,
        EntryDirection direction,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}
