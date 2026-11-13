-- Recurring standing orders and automated transfers table (Phase 4 Step 3)

CREATE TABLE IF NOT EXISTS recurring_transfers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference VARCHAR(64) NOT NULL,
    sender_wallet_id BIGINT NOT NULL,
    recipient_wallet_id BIGINT NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    frequency VARCHAR(20) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NOT NULL,
    end_date DATE NULL,
    next_execution_at TIMESTAMP(6) NOT NULL,
    last_executed_at TIMESTAMP(6) NULL,
    total_executions INT NOT NULL DEFAULT 0,
    max_executions INT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_recurring_transfers_reference UNIQUE (reference),
    CONSTRAINT fk_recurring_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets(id),
    CONSTRAINT fk_recurring_recipient_wallet FOREIGN KEY (recipient_wallet_id) REFERENCES wallets(id),
    CONSTRAINT chk_recurring_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CONSTRAINT chk_recurring_status CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT chk_recurring_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_recurring_distinct_wallets CHECK (sender_wallet_id <> recipient_wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_recurring_due ON recurring_transfers (status, next_execution_at);
CREATE INDEX idx_recurring_sender ON recurring_transfers (sender_wallet_id, created_at DESC);
