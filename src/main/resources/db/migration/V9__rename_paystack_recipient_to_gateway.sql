-- Consolidate on Flutterwave as the single payment provider.
-- Rename the Paystack-specific recipient cache column to a provider-neutral one.
ALTER TABLE beneficiaries
    RENAME COLUMN paystack_recipient_code TO gateway_recipient_code;
