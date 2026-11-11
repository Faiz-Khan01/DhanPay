package com.dhanpay.ledger;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Append-only repository for {@link LedgerTransaction} records (no update or delete operations).
 */
public interface LedgerTransactionRepository extends Repository<LedgerTransaction, Long> {

    LedgerTransaction save(LedgerTransaction ledgerTransaction);

    Optional<LedgerTransaction> findById(Long id);

    Optional<LedgerTransaction> findByReference(String reference);

    Optional<LedgerTransaction> findByTransferId(Long transferId);

    boolean existsByReference(String reference);
}
