-- =============================================================================
-- V4__ledger_entry_monotonic_seq.sql
-- Balance derivation previously ordered by (created_at DESC, id DESC).
-- Multi-leg inserts share identical created_at within one millisecond and
-- UUID ids are random, so "latest entry per wallet" could pick a mid-transaction
-- leg and derive a wrong balance. Add a monotonic entry_seq column backed by a
-- DB sequence and backfill it in commit order.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ledger_entries_entry_seq_seq
    AS BIGINT START WITH 1 INCREMENT BY 1 OWNED BY ledger_entries.id;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS entry_seq BIGINT;

-- Backfill existing rows in (created_at, id) order — best possible order
-- for historical data; new rows get sequence values at insert time.
WITH ordered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at ASC, id ASC) AS rn
    FROM ledger_entries
    WHERE entry_seq IS NULL
)
UPDATE ledger_entries l
SET entry_seq = o.rn
FROM ordered o
WHERE l.id = o.id;

ALTER TABLE ledger_entries
    ALTER COLUMN entry_seq SET NOT NULL;

ALTER TABLE ledger_entries
    ADD CONSTRAINT uq_ledger_entries_entry_seq UNIQUE (entry_seq);

-- Point the default at the sequence so non-JPA inserts also work,
-- advancing past any backfilled values first.
SELECT setval('ledger_entries_entry_seq_seq', COALESCE((SELECT MAX(entry_seq) FROM ledger_entries), 0) + 1, false);

ALTER TABLE ledger_entries
    ALTER COLUMN entry_seq SET DEFAULT nextval('ledger_entries_entry_seq_seq');

CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_seq
    ON ledger_entries (wallet_id, entry_seq DESC);
