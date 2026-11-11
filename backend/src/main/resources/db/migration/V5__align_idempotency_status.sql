-- Align idempotency_records check constraint to accept PROCESSING (Phase 4)

ALTER TABLE idempotency_records DROP CHECK chk_idempotency_status;

ALTER TABLE idempotency_records
    ADD CONSTRAINT chk_idempotency_status
        CHECK (processing_status IN ('IN_PROGRESS', 'PROCESSING', 'COMPLETED', 'FAILED'));
