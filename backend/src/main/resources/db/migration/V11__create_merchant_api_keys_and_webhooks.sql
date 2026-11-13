-- V11: Merchant & Developer API Keys and Signed Webhooks Engine

CREATE TABLE api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL UNIQUE,
    environment VARCHAR(10) NOT NULL DEFAULT 'LIVE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    last_used_at TIMESTAMP NULL,
    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_api_keys_user_id (user_id),
    INDEX idx_api_keys_hash (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_endpoints (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    url VARCHAR(512) NOT NULL,
    secret_key VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    events VARCHAR(512) NOT NULL DEFAULT 'all',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_webhook_endpoints_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_webhooks_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE webhook_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    webhook_endpoint_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    signature VARCHAR(128) NOT NULL,
    http_status INT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(512) NULL,
    dispatched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (webhook_endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    INDEX idx_deliveries_endpoint_id (webhook_endpoint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
