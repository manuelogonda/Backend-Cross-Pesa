-- =============================================================================
-- V2__ledger_integrity_constraints.sql
-- Database-level backstops for double-entry ledger integrity.
-- The application layer enforces these rules; these constraints make it
-- impossible for any other path (manual SQL, bugs, future services) to
-- corrupt the ledger.
-- =============================================================================

-- A ledger line must be a pure debit OR a pure credit, never both, never neither.
ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entry_exclusive_side
    CHECK (debit >= 0 AND credit >= 0 AND (debit = 0) <> (credit = 0));

-- Monetary amounts are never negative.
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_balances_non_negative
    CHECK (balance >= 0 AND locked_balance >= 0);

-- Fast lookup of the latest entry per wallet (drives balance derivation).
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_created
    ON ledger_entries (wallet_id, created_at DESC, id DESC);

-- Fast lookup of all legs belonging to one transaction (audit / reconciliation).
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction
    ON ledger_entries (transaction_id);
