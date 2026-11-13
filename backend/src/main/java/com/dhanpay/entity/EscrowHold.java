package com.dhanpay.entity;

import com.dhanpay.ledger.LedgerAccount;
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

/**
 * Represents funds locked in platform escrow between a buyer and seller wallet.
 */
@Entity
@Table(name = "escrow_holds")
public class EscrowHold {

    private static final int MONETARY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", nullable = false, unique = true, length = 64)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_wallet_id", nullable = false)
    private Wallet buyerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_wallet_id", nullable = false)
    private Wallet sellerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escrow_ledger_account_id", nullable = false)
    private LedgerAccount escrowLedgerAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private EscrowStatus status;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EscrowHold() {
    }

    public EscrowHold(
            String reference,
            Wallet buyerWallet,
            Wallet sellerWallet,
            LedgerAccount escrowLedgerAccount,
            BigDecimal amount,
            String currency,
            EscrowStatus status,
            String description) {
        this.reference = reference;
        this.buyerWallet = buyerWallet;
        this.sellerWallet = sellerWallet;
        this.escrowLedgerAccount = escrowLedgerAccount;
        this.amount = normalizeAmount(amount);
        this.currency = currency;
        this.status = status;
        this.description = description;
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
            this.status = EscrowStatus.HELD;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        this.amount = normalizeAmount(this.amount);
    }

    private static BigDecimal normalizeAmount(BigDecimal value) {
        return value == null ? null : value.setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Wallet getBuyerWallet() {
        return buyerWallet;
    }

    public Wallet getSellerWallet() {
        return sellerWallet;
    }

    public LedgerAccount getEscrowLedgerAccount() {
        return escrowLedgerAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public EscrowStatus getStatus() {
        return status;
    }

    public void setStatus(EscrowStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
