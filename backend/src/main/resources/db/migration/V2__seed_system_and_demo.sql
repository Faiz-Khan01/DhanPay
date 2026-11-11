-- DhanPay System & Demo Seed Data
-- Demo credentials (BCrypt cost 10):
--   Admin   : admin@dhanpay.internal / Admin@1234
--   Customer: aarav@example.com      / Customer@1234
--   Customer: priya@example.com      / Customer@1234
--   Customer: rohan@example.com      / Customer@1234

-- 1. Seed Users (1 Admin + 3 Customers)
INSERT INTO users (id, full_name, email, password_hash, role, status) VALUES
    (1, 'DhanPay System Admin', 'admin@dhanpay.internal', '$2a$10$kcWvZUKnJHMMFcvwfVh2T.f5VsZ7lwc.r8zxK4qdQX5FXPafTTwZO', 'ROLE_ADMIN', 'ACTIVE'),
    (2, 'Aarav Sharma', 'aarav@example.com', '$2a$10$5Y6TkljFboyDvqkfMrNP9uAjROP10io/Bpc7KGq8red25H.78EGvC', 'ROLE_CUSTOMER', 'ACTIVE'),
    (3, 'Priya Patel', 'priya@example.com', '$2a$10$R6bPP.pRlGmCGzQyXpR.YOpYVdwHJR5VTMU5uyZGFVEJzJzn5X4VO', 'ROLE_CUSTOMER', 'ACTIVE'),
    (4, 'Rohan Verma', 'rohan@example.com', '$2a$10$/Ajbakeh137yGHF4CKfHN.fGtrXLG156c34CkD3aBGkES6xvfHmsy', 'ROLE_CUSTOMER', 'ACTIVE');

-- 2. Seed Wallets (matching opening ledger balances)
INSERT INTO wallets (id, user_id, wallet_reference, balance, currency, status, version) VALUES
    (1, 1, 'WLT-SYS-ADMIN-0001', 0.0000, 'INR', 'ACTIVE', 0),
    (2, 2, 'WLT-IND-10000002', 25000.0000, 'INR', 'ACTIVE', 0),
    (3, 3, 'WLT-IND-10000003', 15000.0000, 'INR', 'ACTIVE', 0),
    (4, 4, 'WLT-IND-10000004', 10000.0000, 'INR', 'ACTIVE', 0);

-- 3. Seed Ledger Accounts (System Holding GL Account + 1:1 Wallet Ledger Accounts)
INSERT INTO ledger_accounts (id, wallet_id, account_number, account_type, currency, status) VALUES
    (1, NULL, 'GL-SYS-HOLDING-INR-0001', 'SYSTEM_HOLDING', 'INR', 'ACTIVE'),
    (2, 1, 'LA-ADM-00000001', 'USER_WALLET', 'INR', 'ACTIVE'),
    (3, 2, 'LA-IND-10000002', 'USER_WALLET', 'INR', 'ACTIVE'),
    (4, 3, 'LA-IND-10000003', 'USER_WALLET', 'INR', 'ACTIVE'),
    (5, 4, 'LA-IND-10000004', 'USER_WALLET', 'INR', 'ACTIVE');

-- 4. Seed Balanced System Opening Ledger Transactions
--    Each transaction debits GL-SYS-HOLDING-INR-0001 and credits the customer wallet account.
INSERT INTO ledger_transactions (id, transfer_id, reference, transaction_type, status) VALUES
    (1, NULL, 'LTX-OPENING-10000002', 'SYSTEM_OPENING', 'POSTED'),
    (2, NULL, 'LTX-OPENING-10000003', 'SYSTEM_OPENING', 'POSTED'),
    (3, NULL, 'LTX-OPENING-10000004', 'SYSTEM_OPENING', 'POSTED');

-- 5. Seed Double-Entry Ledger Entries (Total Debits = 50,000.0000, Total Credits = 50,000.0000)
INSERT INTO ledger_entries (ledger_transaction_id, ledger_account_id, direction, amount, currency) VALUES
    (1, 1, 'DEBIT', 25000.0000, 'INR'),
    (1, 3, 'CREDIT', 25000.0000, 'INR'),
    (2, 1, 'DEBIT', 15000.0000, 'INR'),
    (2, 4, 'CREDIT', 15000.0000, 'INR'),
    (3, 1, 'DEBIT', 10000.0000, 'INR'),
    (3, 5, 'CREDIT', 10000.0000, 'INR');

-- 6. Initial Genesis Audit Entry
INSERT INTO audit_logs (actor_user_id, action, resource_type, resource_id, details, ip_address) VALUES
    (1, 'SYSTEM_GENESIS_SEED', 'LEDGER', 'GL-SYS-HOLDING-INR-0001', '{"total_opening_float":"50000.0000","currency":"INR","seeded_customers":3}', '127.0.0.1');
