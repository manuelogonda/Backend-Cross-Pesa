ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS external_reference VARCHAR(255);

ALTER TABLE ledger_entries
    ALTER COLUMN external_reference TYPE VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_ledger_external_reference
    ON ledger_entries (external_reference)
    WHERE external_reference IS NOT NULL;
