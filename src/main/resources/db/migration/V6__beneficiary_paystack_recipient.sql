-- Paystack outbound payouts: persist the gateway-side transfer recipient code
-- so beneficiaries are registered once and reused (idempotent payouts).
ALTER TABLE beneficiaries
    ADD COLUMN IF NOT EXISTS paystack_recipient_code VARCHAR(100);
