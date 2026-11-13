package com.dhanpay.ledger;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Append-only repository for {@link LedgerEntry} postings.
 * Update and delete operations are intentionally omitted to enforce ledger immutability.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, Long> {

    LedgerEntry save(LedgerEntry ledgerEntry);

    <S extends LedgerEntry> List<S> saveAll(Iterable<S> entries);

    Optional<LedgerEntry> findById(Long id);

    List<LedgerEntry> findByLedgerTransactionId(Long ledgerTransactionId);

    @Query("""
            SELECT e FROM LedgerEntry e
            JOIN FETCH e.ledgerAccount la
            LEFT JOIN FETCH la.wallet w
            WHERE e.ledgerTransaction.id = :ledgerTransactionId
            ORDER BY e.id ASC
            """)
    List<LedgerEntry> findAllWithAccountByTransactionId(@Param("ledgerTransactionId") Long ledgerTransactionId);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0.0000)
            FROM LedgerEntry e
            WHERE e.ledgerAccount.id = :accountId
              AND e.direction = :direction
            """)
    BigDecimal sumAmountByAccountIdAndDirection(
            @Param("accountId") Long accountId,
            @Param("direction") EntryDirection direction);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0.0000)
            FROM LedgerEntry e
            WHERE e.ledgerAccount.id = :accountId
              AND e.direction = :direction
              AND e.createdAt < :before
            """)
    BigDecimal sumAmountByAccountIdAndDirectionBefore(
            @Param("accountId") Long accountId,
            @Param("direction") EntryDirection direction,
            @Param("before") Instant before);

    @Query("""
            SELECT e FROM LedgerEntry e
            JOIN FETCH e.ledgerTransaction lt
            LEFT JOIN FETCH lt.transfer t
            LEFT JOIN FETCH t.senderWallet sw
            LEFT JOIN FETCH sw.user su
            LEFT JOIN FETCH t.recipientWallet rw
            LEFT JOIN FETCH rw.user ru
            WHERE e.ledgerAccount.id = :accountId
              AND e.createdAt >= :from
              AND e.createdAt <= :to
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<LedgerEntry> findEntriesForAccountBetween(
            @Param("accountId") Long accountId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(SUM(e.amount), 0.0000)
            FROM LedgerEntry e
            WHERE e.direction = :direction
            """)
    BigDecimal sumAllByDirection(@Param("direction") EntryDirection direction);
}
