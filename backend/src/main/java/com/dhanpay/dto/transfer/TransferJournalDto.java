package com.dhanpay.dto.transfer;

import com.dhanpay.ledger.LedgerTransactionStatus;
import com.dhanpay.ledger.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Double-entry journal breakdown for a settled transfer.
 */
public record TransferJournalDto(
        String transferReference,
        String ledgerReference,
        LedgerTransactionType transactionType,
        LedgerTransactionStatus ledgerStatus,
        BigDecimal amount,
        String currency,
        String senderWalletReference,
        String recipientWalletReference,
        String description,
        Instant createdAt,
        List<JournalLineDto> entries
) {
}
