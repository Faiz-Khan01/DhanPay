-- Escrow holds table and platform GL escrow account (Phase 3 Step 3)

INSERT INTO ledger_accounts (wallet_id, account_number, account_type, currency, status)
SELECT NULL, 'GL-SYS-ESCROW-INR-0001', 'ESCROW', 'INR', 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_accounts WHERE account_number = 'GL-SYS-ESCROW-INR-0001'
);

CREATE TABLE IF NOT EXISTS escrow_holds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(64) NOT NULL,
    buyer_wallet_id BIGINT NOT NULL,
    seller_wallet_id BIGINT NOT NULL,
    escrow_ledger_account_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_escrow_holds_reference UNIQUE (reference),
    CONSTRAINT fk_escrow_buyer_wallet FOREIGN KEY (buyer_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_escrow_seller_wallet FOREIGN KEY (seller_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_escrow_ledger_account FOREIGN KEY (escrow_ledger_account_id) REFERENCES ledger_accounts(id),
    CONSTRAINT chk_escrow_status CHECK (status IN ('HELD', 'RELEASED', 'REFUNDED')),
    CONSTRAINT chk_escrow_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_escrow_distinct_wallets CHECK (buyer_wallet_id <> seller_wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_escrow_buyer_created ON escrow_holds (buyer_wallet_id, created_at DESC);
CREATE INDEX idx_escrow_seller_created ON escrow_holds (seller_wallet_id, created_at DESC);
