package com.dhanpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only immutable double-entry ledger line item (DEBIT or CREDIT).
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    private static final int MONETARY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_transaction_id", nullable = false, updatable = false)
    private LedgerTransaction ledgerTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_account_id", nullable = false, updatable = false)
    private LedgerAccount ledgerAccount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", nullable = false, updatable = false, length = 10)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            LedgerTransaction ledgerTransaction,
            LedgerAccount ledgerAccount,
            EntryDirection direction,
            BigDecimal amount,
            String currency) {
        this.ledgerTransaction = Objects.requireNonNull(ledgerTransaction, "ledgerTransaction must not be null");
        this.ledgerAccount = Objects.requireNonNull(ledgerAccount, "ledgerAccount must not be null");
        this.direction = Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be strictly positive");
        }
        this.amount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LedgerTransaction getLedgerTransaction() {
        return ledgerTransaction;
    }

    public LedgerAccount getLedgerAccount() {
        return ledgerAccount;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
