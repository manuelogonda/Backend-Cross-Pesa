-- =============================================================================
-- V3__allow_flagged_transaction_status.sql
-- The application's FraudDetectionService sets status = 'FLAGGED' on
-- suspicious transactions, but the V1 CHECK constraint on transactions.status
-- did not include that value, so any flagged transaction insert would fail
-- at the database level.
-- =============================================================================

ALTER TABLE transactions
    DROP CONSTRAINT IF EXISTS transactions_status_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'FLAGGED'));
