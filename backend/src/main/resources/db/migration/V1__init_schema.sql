-- DhanPay Core Transaction & Double-Entry Ledger Schema (MySQL 8.0 InnoDB)
-- Monetary precision: DECIMAL(19,4) across all balances, transfers, and ledger entries.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_role_status ON users (role, status);

CREATE TABLE wallets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    wallet_reference VARCHAR(64) NOT NULL,
    balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT uk_wallets_user_id UNIQUE (user_id),
    CONSTRAINT uk_wallets_reference UNIQUE (wallet_reference),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_wallets_balance_non_negative CHECK (balance >= 0.0000),
    CONSTRAINT chk_wallets_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_wallets_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_wallets_status ON wallets (status);

CREATE TABLE transfers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(64) NOT NULL,
    sender_wallet_id BIGINT NOT NULL,
    recipient_wallet_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    description VARCHAR(255) NULL,
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_transfers PRIMARY KEY (id),
    CONSTRAINT uk_transfers_reference UNIQUE (reference),
    CONSTRAINT fk_transfers_sender_wallet FOREIGN KEY (sender_wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfers_recipient_wallet FOREIGN KEY (recipient_wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0.0000),
    CONSTRAINT chk_transfers_distinct_wallets CHECK (sender_wallet_id <> recipient_wallet_id),
    CONSTRAINT chk_transfers_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_transfers_status CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_transfers_sender_created ON transfers (sender_wallet_id, created_at);
CREATE INDEX idx_transfers_recipient_created ON transfers (recipient_wallet_id, created_at);
CREATE INDEX idx_transfers_status_created ON transfers (status, created_at);

CREATE TABLE ledger_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NULL,
    account_number VARCHAR(64) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ledger_accounts PRIMARY KEY (id),
    CONSTRAINT uk_ledger_accounts_wallet_id UNIQUE (wallet_id),
    CONSTRAINT uk_ledger_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_ledger_accounts_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ledger_accounts_type CHECK (account_type IN ('USER_WALLET', 'SYSTEM_HOLDING', 'SYSTEM_FEE', 'SYSTEM_SUSPENSE')),
    CONSTRAINT chk_ledger_accounts_currency CHECK (CHAR_LENGTH(currency) = 3),
    CONSTRAINT chk_ledger_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ledger_accounts_type_status ON ledger_accounts (account_type, status);

CREATE TABLE ledger_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NULL,
    reference VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ledger_transactions PRIMARY KEY (id),
    CONSTRAINT uk_ledger_transactions_reference UNIQUE (reference),
    CONSTRAINT fk_ledger_transactions_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ledger_transactions_type CHECK (transaction_type IN ('P2P_TRANSFER', 'SYSTEM_OPENING', 'REVERSAL', 'ADJUSTMENT')),
    CONSTRAINT chk_ledger_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'VOIDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ledger_transactions_transfer_id ON ledger_transactions (transfer_id);
CREATE INDEX idx_ledger_transactions_type_created ON ledger_transactions (transaction_type, created_at);

CREATE TABLE ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ledger_transaction_id BIGINT NOT NULL,
    ledger_account_id BIGINT NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0.0000),
    CONSTRAINT chk_ledger_entries_currency CHECK (CHAR_LENGTH(currency) = 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ledger_entries_tx_id ON ledger_entries (ledger_transaction_id);
CREATE INDEX idx_ledger_entries_account_created ON ledger_entries (ledger_account_id, created_at);
CREATE INDEX idx_ledger_entries_account_direction ON ledger_entries (ledger_account_id, direction);

CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'IN_PROGRESS',
    transfer_id BIGINT NULL,
    response_payload JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_idempotency_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_idempotency_status CHECK (processing_status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idempotency_expires_at ON idempotency_records (expires_at);
CREATE INDEX idx_idempotency_transfer_id ON idempotency_records (transfer_id);

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NULL,
    details TEXT NULL,
    ip_address VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_user_id, created_at);
CREATE INDEX idx_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_logs_action_created ON audit_logs (action, created_at);
