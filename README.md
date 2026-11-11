# DhanPay — FinTech Transaction Processing Engine (Phase 1)

DhanPay is a double-entry ledger and wallet transaction processing platform built with **Java 17**, **Spring Boot 3.3.5**, **MySQL 8.0 (InnoDB)**, **Flyway**, and **React 18 + Vite + Tailwind CSS**.

## Project Structure

```text
DhanPay/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dhanpay/DhanPayApplication.java
│       └── resources/
│           ├── application.yml
│           └── db/migration/
│               ├── V1__init_schema.sql
│               └── V2__seed_system_and_demo.sql
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── vite.config.js
│   └── src/
│       ├── App.jsx
│       ├── index.css
│       └── main.jsx
├── .env.example
├── docker-compose.yml
└── README.md
```

## Financial & Database Design Guarantees

- **Strict Fixed-Point Arithmetic**: All monetary columns (`wallets.balance`, `transfers.amount`, `ledger_entries.amount`) use `DECIMAL(19,4)` with DB-level `CHECK` constraints (`>= 0.0000` for wallet balances, `> 0.0000` for transfer/entry amounts).
- **Double-Entry Accounting**: Every value movement is recorded as a balanced `ledger_transactions` batch of `DEBIT` and `CREDIT` `ledger_entries`. Opening balances in `V2__seed_system_and_demo.sql` debit `GL-SYS-HOLDING-INR-0001` and credit each customer wallet ledger account so total debits equal total credits (`INR 50,000.0000`) from inception.
- **Referential Integrity**: All foreign keys enforce `ON DELETE RESTRICT` to prevent destructive cascades on financial records.
- **Schema Governance**: Hibernate runs with `ddl-auto: validate` and `open-in-view: false`; Flyway is the sole owner of DDL evolution.
- **Concurrency & Idempotency**: Wallets carry an optimistic locking `version` column, and `idempotency_records` enforces uniqueness on `(user_id, idempotency_key)` with request fingerprinting and JSON response caching.

## Seeded Accounts (`V2__seed_system_and_demo.sql`)

| Role | Name | Email | Password | Wallet Ref | Opening Balance (INR) |
| :--- | :--- | :--- | :--- | :--- | ---: |
| `ROLE_ADMIN` | DhanPay System Admin | `admin@dhanpay.internal` | `Admin@1234` | `WLT-SYS-ADMIN-0001` | `0.0000` |
| `ROLE_CUSTOMER` | Aarav Sharma | `aarav@example.com` | `Customer@1234` | `WLT-IND-10000002` | `25,000.0000` |
| `ROLE_CUSTOMER` | Priya Patel | `priya@example.com` | `Customer@1234` | `WLT-IND-10000003` | `15,000.0000` |
| `ROLE_CUSTOMER` | Rohan Verma | `rohan@example.com` | `Customer@1234` | `WLT-IND-10000004` | `10,000.0000` |

## Quick Start

1. Copy `.env.example` to `.env` and adjust credentials if needed:
   ```bash
   cp .env.example .env
   ```
2. Start MySQL 8.0 and the Spring Boot backend via Docker Compose:
   ```bash
   docker compose up --build -d
   ```
3. Verify health and Swagger UI:
   - Health: `http://localhost:8080/actuator/health`
   - OpenAPI UI: `http://localhost:8080/swagger-ui.html`
4. Start the frontend dev server (optional in Phase 1):
   ```bash
   cd frontend && npm install && npm run dev
   ```
