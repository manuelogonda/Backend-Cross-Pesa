-- V1__init_crosspesa_schema.sql

-- 0. GLOBAL TRIGGER FUNCTION
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END
$$ LANGUAGE plpgsql^^

-- 1. USERS
CREATE TABLE IF NOT EXISTS users (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) UNIQUE NOT NULL,

    email_verified BOOLEAN  NOT NULL DEFAULT FALSE,

    password_hash  VARCHAR(255),

    first_name  VARCHAR(100)  NOT NULL,

    last_name VARCHAR(100) NOT NULL,

    phone_number   VARCHAR(20) UNIQUE  NOT NULL,

    phone_verified   BOOLEAN             NOT NULL DEFAULT FALSE,

    auth_provider_id     VARCHAR(150) UNIQUE,

    auth_provider        VARCHAR(20),

    country_of_residence VARCHAR(2)          NOT NULL DEFAULT 'KE'
        CHECK (country_of_residence IN (
                                        'KE', 'US', 'CN', 'JP',
                                        'GB', 'CA', 'AU', 'PK',
                                        'NL', 'AE', 'SA',
                                        'DE', 'FR', 'SE', 'FI', 'IT', 'ES',
                                        'AE', 'SA', 'QA', 'KW', 'OM', 'BH', 'JO', 'IL',
                                        'UG', 'TZ', 'RW', 'SS', 'ZA', 'NG', 'GH', 'ET',
                                        'SO'
            )),

    date_of_birth        DATE              ,

    id_type              VARCHAR(50)    DEFAULT 'NATIONAL_ID'
        CHECK (id_type IN ('NATIONAL_ID', 'PASSPORT')),

    id_number            VARCHAR(100) UNIQUE ,

    role                 VARCHAR(20)         NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'MERCHANT', 'ADMIN')),

    status               VARCHAR(20)         NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LOCKED')),

    kyc_status           VARCHAR(20)         NOT NULL DEFAULT 'PENDING'
        CHECK (kyc_status IN ('PENDING', 'APPROVED', 'REJECTED')),

    kyc_level            SMALLINT            NOT NULL DEFAULT 1
        CHECK (kyc_level IN (1, 2, 3)),

    created_at           TIMESTAMPTZ         NOT NULL
                                                      DEFAULT CURRENT_TIMESTAMP,

    updated_at           TIMESTAMPTZ         NOT NULL
                                                      DEFAULT CURRENT_TIMESTAMP
)^^

CREATE TRIGGER update_users_modtime
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^


-- 2. The Beneficiaries Table
CREATE TABLE beneficiaries
(
    id               UUID PRIMARY KEY             DEFAULT gen_random_uuid(),

    user_id          UUID                NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    first_name       VARCHAR(50)         NOT NULL,

    last_name        VARCHAR(50)         NOT NULL,

    beneficiary_type VARCHAR(50)         NOT NULL DEFAULT 'INDIVIDUAL',
    CHECK (beneficiary_type IN ('INDIVIDUAL', 'ORGANIZATION', 'BUSINESS')),

    email            VARCHAR(100) UNIQUE NOT NULL,

    phone_number     VARCHAR(20) UNIQUE  NOT NULL,

    country_code     VARCHAR(2)          NOT NULL,

    city             VARCHAR(50),

    payout_method    VARCHAR(50)         NOT NULL DEFAULT 'BANK_TRANSFER'
        CHECK (payout_method IN ('BANK_TRANSFER', 'MOBILE_MONEY', 'CARD_PAYMENT')),

    payout_provider  VARCHAR(50)         NOT NULL DEFAULT 'M-PESA'
        CHECK (payout_provider IN ('M-PESA', 'EQUITY BANK',
                                   'VISA', 'MASTERCARD')),

    account_number   VARCHAR(50)         NOT NULL,

    account_currency VARCHAR(3)          NOT NULL DEFAULT 'KES',

    created_at       TIMESTAMPTZ                  DEFAULT CURRENT_TIMESTAMP,

    updated_at       TIMESTAMPTZ                  DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_beneficiary_routing
        UNIQUE (user_id, payout_provider, account_number)
)^^

CREATE TRIGGER trigger_update_beneficiaries_timestamp
    BEFORE UPDATE
    ON beneficiaries
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^

CREATE TABLE IF NOT EXISTS wallets
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),

    -- CHANGED TO NULL: Allows system accounts to belong directly to the platform
    user_id        UUID                    NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    wallet_type    VARCHAR(30)    NOT NULL DEFAULT 'USER_RETAIL'
        CHECK (wallet_type IN (
                               'USER_RETAIL',
                               'SYSTEM_MARKUP',
                               'SYSTEM_ROUTING',
                               'SYSTEM_LIQUIDITY'
            )),

    currency       VARCHAR(3)     NOT NULL
        CHECK (currency IN ('KES', 'USD', 'CNY',
                            'JPY', 'GBP', 'CAD', 'AUD', 'PKR',
                            'AED', 'SAR', 'EUR', 'SEK'
            )),

    balance        NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,

    locked_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000
        CHECK (locked_balance >= 0.0000),

    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FROZEN', 'SUSPENDED')),

    created_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ENFORCES INTEGRITY PER WALLET CLASS TYPE
    -- 1. Standard customers cannot spend past zero and must link to a valid user record.
    -- 2. System wallets must never link to a standard customer account identifier.
    CONSTRAINT check_wallet_class_rules CHECK (
        (wallet_type = 'USER_RETAIL' AND user_id IS NOT NULL AND balance >= 0.0000) OR
        (wallet_type IN ('SYSTEM_MARKUP', 'SYSTEM_ROUTING', 'SYSTEM_LIQUIDITY') AND user_id IS NULL)
        ),

    -- Ensures a user can never lock more money than they actually have
    CONSTRAINT check_valid_reservation CHECK (balance >= locked_balance)
);

-- ==========================================
-- SMART UNIQUE CONSTRAINTS (Fixed Partial Indexes)
-- ==========================================

-- 1. Enforce 1-User-1-Wallet per currency STRICTLY for retail customers.
-- (Allows 1 customer to hold multiple currency accounts if your capstone expands!)
CREATE UNIQUE INDEX uq_user_retail_wallet
    ON wallets (user_id, currency)
    WHERE wallet_type = 'USER_RETAIL';

-- 2. FIXED: Enforce that the platform has exactly ONE system wallet per currency per type.
-- (Removes the NULL user_id problem entirely)
CREATE UNIQUE INDEX uq_system_wallet_currency
    ON wallets (currency, wallet_type)
    WHERE wallet_type != 'USER_RETAIL';

CREATE TRIGGER update_wallets_modtime
    BEFORE UPDATE
    ON wallets
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^

CREATE TABLE IF NOT EXISTS transactions
(
    id                    UUID PRIMARY KEY       DEFAULT gen_random_uuid(),

    sender_id             UUID                   NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    beneficiary_id        UUID                   NULL
        REFERENCES beneficiaries (id) ON DELETE RESTRICT,

    source_wallet_id      UUID                   NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    -- COUPLING IN ACTION: For cross-border, your backend inserts the
    -- matching destination corridor's 'SYSTEM_LIQUIDITY' Wallet UUID here.
    destination_wallet_id UUID                   NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    source_currency       VARCHAR(3)             NOT NULL,
    destination_currency  VARCHAR(3)             NOT NULL,

    -- 1. THE AMOUNTS (Separating Gross from Net)
    gross_amount          NUMERIC(18, 4)         NOT NULL
        CHECK (gross_amount > 0.0000),

    net_amount            NUMERIC(18, 4)         NOT NULL
        CHECK (net_amount > 0.0000),

    -- 2. THE FEE BREAKDOWN (All stored in source_currency)
    markup_fee            NUMERIC(18, 4)         NOT NULL DEFAULT 0.0000,
    routing_fee           NUMERIC(18, 4)         NOT NULL DEFAULT 0.0000,
    total_fee             NUMERIC(18, 4)         NOT NULL DEFAULT 0.0000,

    -- AIRTIGHT MATHEMATICAL INTEGRITY LOCKS
    CONSTRAINT chk_total_fee_match CHECK (total_fee = markup_fee + routing_fee),
    CONSTRAINT chk_net_amount_match CHECK (net_amount = gross_amount - total_fee),

    -- 3. THE FX AUDIT TRAIL
    usd_normalization_rate NUMERIC(18, 6)        NOT NULL
        CHECK (usd_normalization_rate > 0.000000),

    fx_rate_applied       NUMERIC(18, 6)         NOT NULL
        CHECK (fx_rate_applied > 0.000000),

    destination_amount    NUMERIC(18, 4)         NOT NULL
        CHECK (destination_amount > 0.0000),

    -- 4. EXTERNAL GATEWAYS
    funding_gateway       VARCHAR(50),
    gateway_reference     VARCHAR(150), -- Removed flat UNIQUE constraint

    payout_gateway        VARCHAR(50),
    payout_reference      VARCHAR(150), -- Removed flat UNIQUE constraint

    status                VARCHAR(30)            NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),

    idempotency_key       UUID UNIQUE            NOT NULL,

    created_at            TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- SAFE CONDITIONAL EXCLUSIONS (Partial Indexes)
-- ==========================================

-- Ensures gateway reference fields remain perfectly unique ONLY when they hold a non-null string value.
CREATE UNIQUE INDEX uq_gateway_reference_active
    ON transactions (gateway_reference)
    WHERE gateway_reference IS NOT NULL;

CREATE UNIQUE INDEX uq_payout_reference_active
    ON transactions (payout_reference)
    WHERE payout_reference IS NOT NULL;

CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^


CREATE TABLE IF NOT EXISTS ledger_entries
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transaction_id       UUID NOT NULL
        REFERENCES transactions (id) ON DELETE RESTRICT,

    wallet_id            UUID NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    entry_class          VARCHAR(50) NOT NULL
        CHECK (entry_class IN (
                               'PRINCIPAL_TRANSFER',
                               'MARKUP_FEE',
                               'ROUTING_FEE',
                               'FX_CLEARING',
                               'DEPOSIT',
                               'WITHDRAWAL',
                               'REFUND',
                               'TREASURY_ADJUSTMENT'
            )),

    debit                NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    credit               NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,

    currency             VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),

    balance_after        NUMERIC(18, 4) NOT NULL,   -- calculated in service

    description          VARCHAR(255) NOT NULL,

    -- Pricing engine audit trail
    routing_pair         VARCHAR(10),
    markup_tiers_applied VARCHAR(100),
    usd_baseline_amount  NUMERIC(18, 4),

    -- Optional but useful for gateway reconciliation
    external_reference   VARCHAR(100),

    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Double-entry invariant
    CONSTRAINT chk_debit_credit_exclusive CHECK (
        (debit > 0 AND credit = 0) OR
        (credit > 0 AND debit = 0)
        ),

    -- Safety
    CONSTRAINT chk_non_negative_amounts CHECK (
        debit >= 0 AND credit >= 0
        )
);

-- 6. EXCHANGE RATES
CREATE TABLE IF NOT EXISTS fx_rates
(
    id                   UUID PRIMARY KEY         DEFAULT gen_random_uuid(),

    source_currency      VARCHAR(3)     NOT NULL,

    destination_currency VARCHAR(3)     NOT NULL,

    -- The raw mid-market rate provided by Open Exchange Rates (e.g., 1 USD = 129.500000 KES)
    rate                 NUMERIC(18, 6) NOT NULL
        CHECK (rate > 0.000000),

    valid_from           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Defines when this specific rate lock expires and a new pull is required
    expires_at           TIMESTAMPTZ    NOT NULL,

    created_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Safety Check Constraints
    CONSTRAINT chk_different_currencies CHECK (source_currency <> destination_currency),
    CONSTRAINT chk_valid_expiry_window CHECK (expires_at > valid_from)
);
CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON fx_rates
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^


-- 7. NOTIFICATIONS
CREATE TABLE IF NOT EXISTS notifications
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),

    user_id           UUID         NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    transaction_id    UUID
        REFERENCES transactions (id) ON DELETE RESTRICT,

    title             VARCHAR(150) NOT NULL,

    message           TEXT         NOT NULL,

    metadata          JSONB, -- Stores template variables or exact Twilio API responses

    notification_type VARCHAR(20)              DEFAULT 'SMS'
        CHECK (notification_type IN ('EMAIL', 'SMS', 'IN_APP')),

    retry_count       INT                      DEFAULT 0,
    error_message     TEXT,
    idempotency_key   UUID UNIQUE,

    status            VARCHAR(20)  NOT NULL    DEFAULT 'UNREAD'
        CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),

    created_at        TIMESTAMPTZ  NOT NULL
                                               DEFAULT CURRENT_TIMESTAMP,

    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
)^^
CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON notifications
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^

-- 8 kyc submission
CREATE TABLE kyc_submissions (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- The unique tracking ID provided by Smile ID SDK
                                 smile_job_id VARCHAR(100) UNIQUE NOT NULL,

    -- Document Metadata
                                 document_type VARCHAR(50) NOT NULL, -- e.g., 'NATIONAL_ID', 'PASSPORT'
                                 document_country VARCHAR(10) NOT NULL, -- e.g., 'KE', 'NG'

    -- Cloudinary URLs (Stored after webhook confirmation)
                                 id_image_url VARCHAR(500),
                                 selfie_image_url VARCHAR(500),

    -- Submission Status (PENDING, APPROVED, REJECTED, FAILED)
                                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Context for failures or admin rejections
                                 rejection_reason TEXT,

    -- Admin tracking (Who approved/rejected this?)
                                 reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
                                 reviewed_at TIMESTAMP WITH TIME ZONE,

                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON kyc_submissions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^


-- 8. INDEXES
-- Index for O(1) live rate lookups by currency pair and expiry window
CREATE INDEX idx_fx_rates_active_lookup
    ON fx_rates (source_currency, destination_currency, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users (phone_number);
CREATE INDEX IF NOT EXISTS idx_users_kyc ON users (kyc_status);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_fx_rates_lookup ON fx_rates (source_currency, destination_currency, expires_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_sender ON transactions (sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver ON transactions (beneficiary_id) WHERE beneficiary_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_active_status ON transactions (status) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_transactions_sender_history ON transactions (sender_id, created_at DESC);
CREATE INDEX idx_transactions_gateway_ref ON transactions (gateway_reference) WHERE gateway_reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_ledger_wallet ON ledger_entries (wallet_id);
CREATE INDEX idx_ledger_transaction ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries (created_at);
CREATE INDEX idx_kyc_smile_job_id ON kyc_submissions(smile_job_id);
CREATE INDEX idx_kyc_user_id ON kyc_submissions(user_id);
CREATE INDEX idx_kyc_status ON kyc_submissions(status);

CREATE INDEX IF NOT EXISTS idx_ledger_wallet_created
    ON ledger_entries (wallet_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ledger_transaction
    ON ledger_entries (transaction_id);

CREATE INDEX IF NOT EXISTS idx_ledger_entry_class
    ON ledger_entries (entry_class);

CREATE INDEX IF NOT EXISTS idx_ledger_external_ref
    ON ledger_entries (external_reference)
    WHERE external_reference IS NOT NULL;

