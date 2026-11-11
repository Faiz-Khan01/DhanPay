-- DhanPay Phase 2: Saved Beneficiaries / Quick Contacts Book

CREATE TABLE beneficiaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    target_wallet_id BIGINT NOT NULL,
    nickname VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_beneficiaries PRIMARY KEY (id),
    CONSTRAINT uk_beneficiaries_owner_target UNIQUE (owner_user_id, target_wallet_id),
    CONSTRAINT fk_beneficiaries_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_beneficiaries_target_wallet FOREIGN KEY (target_wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_beneficiaries_owner ON beneficiaries (owner_user_id, created_at);
