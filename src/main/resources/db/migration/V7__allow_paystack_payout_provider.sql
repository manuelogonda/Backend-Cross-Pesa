-- Allow PAYSTACK as a payout provider for beneficiaries (Paystack outbound payouts).
ALTER TABLE beneficiaries
    DROP CONSTRAINT IF EXISTS beneficiaries_payout_provider_check;
ALTER TABLE beneficiaries
    ADD CONSTRAINT beneficiaries_payout_provider_check
    CHECK (payout_provider IN ('M-PESA', 'EQUITY BANK', 'VISA', 'MASTERCARD', 'PAYSTACK'));
