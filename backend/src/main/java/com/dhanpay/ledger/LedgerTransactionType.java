package com.dhanpay.ledger;

/**
 * Classification of immutable double-entry ledger transaction batches.
 */
public enum LedgerTransactionType {
    P2P_TRANSFER,
    SYSTEM_OPENING,
    REVERSAL,
    ADJUSTMENT
}
