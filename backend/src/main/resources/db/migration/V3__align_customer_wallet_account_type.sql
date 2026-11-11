-- Align ledger_accounts.account_type to support CUSTOMER_WALLET (Phase 2)
-- Updates existing USER_WALLET seed rows to CUSTOMER_WALLET and updates CHECK constraint.

ALTER TABLE ledger_accounts DROP CHECK chk_ledger_accounts_type;

UPDATE ledger_accounts
SET account_type = 'CUSTOMER_WALLET'
WHERE account_type = 'USER_WALLET';

ALTER TABLE ledger_accounts
    ADD CONSTRAINT chk_ledger_accounts_type
        CHECK (account_type IN ('CUSTOMER_WALLET', 'SYSTEM_HOLDING', 'SYSTEM_FEE', 'SYSTEM_SUSPENSE'));
