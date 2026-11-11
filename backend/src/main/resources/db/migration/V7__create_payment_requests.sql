-- DhanPay Phase 2: P2P Payment Requests

CREATE TABLE payment_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(64) NOT NULL,
    requester_wallet_id BIGINT NOT NULL,
    payer_wallet_id BIGINT NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(255) NULL,
    transfer_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_payment_requests PRIMARY KEY (id),
    CONSTRAINT uk_payment_requests_reference UNIQUE (reference),
    CONSTRAINT fk_payment_requests_requester FOREIGN KEY (requester_wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_requests_payer FOREIGN KEY (payer_wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_requests_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_requests_amount_positive CHECK (amount > 0.0000),
    CONSTRAINT chk_payment_requests_distinct_wallets CHECK (requester_wallet_id <> payer_wallet_id),
    CONSTRAINT chk_payment_requests_status CHECK (status IN ('PENDING', 'PAID', 'DECLINED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_requests_payer_status ON payment_requests (payer_wallet_id, status, created_at);
CREATE INDEX idx_payment_requests_requester_status ON payment_requests (requester_wallet_id, status, created_at);
