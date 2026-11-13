package com.dhanpay.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents an automated standing order recurring transfer between two wallets.
 */
@Entity
@Table(name = "recurring_transfers")
public class RecurringTransfer {

    private static final int MONETARY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 64)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_wallet_id", nullable = false)
    private Wallet senderWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_wallet_id", nullable = false)
    private Wallet recipientWallet;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "frequency", nullable = false, length = 20)
    private RecurringFrequency frequency;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private RecurringStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_execution_at", nullable = false)
    private Instant nextExecutionAt;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @Column(name = "total_executions", nullable = false)
    private int totalExecutions = 0;

    @Column(name = "max_executions")
    private Integer maxExecutions;

    @Column(name = "failure_count", nullable = false)
    private int failureCount = 0;

    @Column(name = "last_failure_reason", length = 255)
    private String lastFailureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RecurringTransfer() {
    }

    public RecurringTransfer(
            String reference,
            Wallet senderWallet,
            Wallet recipientWallet,
            BigDecimal amount,
            String currency,
            RecurringFrequency frequency,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            Instant nextExecutionAt,
            Integer maxExecutions) {
        this.reference = reference;
        this.senderWallet = senderWallet;
        this.recipientWallet = recipientWallet;
        this.amount = normalizeAmount(amount);
        this.currency = currency;
        this.frequency = frequency;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextExecutionAt = nextExecutionAt;
        this.maxExecutions = maxExecutions;
        this.status = RecurringStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.amount = normalizeAmount(this.amount);
        if (this.currency == null) {
            this.currency = "INR";
        }
        if (this.status == null) {
            this.status = RecurringStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        this.amount = normalizeAmount(this.amount);
    }

    private static BigDecimal normalizeAmount(BigDecimal val) {
        return val == null ? null : val.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Wallet getSenderWallet() {
        return senderWallet;
    }

    public Wallet getRecipientWallet() {
        return recipientWallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public String getDescription() {
        return description;
    }

    public RecurringStatus getStatus() {
        return status;
    }

    public void setStatus(RecurringStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getNextExecutionAt() {
        return nextExecutionAt;
    }

    public void setNextExecutionAt(Instant nextExecutionAt) {
        this.nextExecutionAt = nextExecutionAt;
    }

    public Instant getLastExecutedAt() {
        return lastExecutedAt;
    }

    public void setLastExecutedAt(Instant lastExecutedAt) {
        this.lastExecutedAt = lastExecutedAt;
    }

    public int getTotalExecutions() {
        return totalExecutions;
    }

    public void incrementTotalExecutions() {
        this.totalExecutions++;
    }

    public Integer getMaxExecutions() {
        return maxExecutions;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void recordFailure(String reason) {
        this.failureCount++;
        this.lastFailureReason = reason;
    }

    public void resetFailureCount() {
        this.failureCount = 0;
        this.lastFailureReason = null;
    }

    public String getLastFailureReason() {
        return lastFailureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
