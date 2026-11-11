-- Add ESCROW account type to ledger_accounts check constraint (Phase 3)

ALTER TABLE ledger_accounts DROP CHECK chk_ledger_accounts_type;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT chk_ledger_accounts_type
        CHECK (account_type IN ('CUSTOMER_WALLET', 'SYSTEM_HOLDING', 'ESCROW', 'SYSTEM_FEE', 'SYSTEM_SUSPENSE'));
