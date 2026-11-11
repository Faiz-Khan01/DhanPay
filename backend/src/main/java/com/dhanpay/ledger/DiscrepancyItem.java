package com.dhanpay.ledger;

import java.math.BigDecimal;

/**
 * Represents a mismatch between a wallet's materialized balance and its ledger-derived balance.
 */
public record DiscrepancyItem(
        String walletRef,
        BigDecimal currentBalance,
        BigDecimal ledgerDerivedBalance,
        BigDecimal delta
) {
}
