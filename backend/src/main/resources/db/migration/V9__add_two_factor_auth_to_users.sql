-- Add Two-Factor Authentication (TOTP RFC 6238) columns to users table (Phase 4 Step 2)

ALTER TABLE users
    ADD COLUMN totp_secret VARCHAR(64) NULL,
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_backup_codes VARCHAR(500) NULL;
