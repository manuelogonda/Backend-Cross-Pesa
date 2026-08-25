-- Paystack transfer recipients require the destination bank/network code
-- (bank_code) for both nuban and mobile_money recipient types.
ALTER TABLE beneficiaries
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(20);
