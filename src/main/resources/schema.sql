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


-- 3. WALLETS
CREATE TABLE IF NOT EXISTS wallets
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),

    user_id        UUID           NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    -- THE DISCRIMINATOR COLUMN
    wallet_type    VARCHAR(30)    NOT NULL DEFAULT 'USER_RETAIL'
        CHECK (wallet_type IN (
                               'USER_RETAIL',      -- Standard customer wallet (1 per user)
                               'SYSTEM_MARKUP',    --  profit from volume tiers
                               'SYSTEM_ROUTING',   --  profit from corridor routing
                               'SYSTEM_LIQUIDITY'  --  holding account for gateway clearing
            )),

    currency       VARCHAR(3)     NOT NULL
        CHECK (currency IN ('KES', 'USD', 'CNY',
                            'JPY', 'GBP', 'CAD', 'AUD', 'PKR',
                            'AED', 'SAR', 'EUR', 'SEK'
            )),

    balance        NUMERIC(18, 4) NOT NULL DEFAULT 0.0000
        CHECK (balance >= 0.0000),

    locked_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000
        CHECK (locked_balance >= 0.0000),

    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FROZEN', 'SUSPENDED')),

    created_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensures a user can never lock more money than they actually have
    CONSTRAINT check_valid_reservation CHECK (balance >= locked_balance)
);

-- ==========================================
-- SMART UNIQUE CONSTRAINTS (Partial Indexes)
-- ==========================================

-- 1. Enforce 1-User-1-Wallet STRICTLY for retail customers only.
CREATE UNIQUE INDEX uq_user_retail_wallet
    ON wallets (user_id)
    WHERE wallet_type = 'USER_RETAIL';

-- 2. Enforce that the system only has exactly ONE wallet per currency per type.
-- (e.g., You can only have one 'SYSTEM_MARKUP' wallet for 'GBP').
CREATE UNIQUE INDEX uq_system_wallet_currency
    ON wallets (user_id, currency, wallet_type)
    WHERE wallet_type != 'USER_RETAIL';

CREATE TRIGGER update_wallets_modtime
    BEFORE UPDATE
    ON wallets
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^

-- 4. TRANSACTIONS
CREATE TABLE IF NOT EXISTS transactions
(
    id                    UUID PRIMARY KEY       DEFAULT gen_random_uuid(),

    sender_id             UUID                   NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    beneficiary_id        UUID                   NULL
        REFERENCES beneficiaries (id) ON DELETE RESTRICT,

    source_wallet_id      UUID                   NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

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
    CHECK (total_fee = markup_fee + routing_fee),
    CHECK (net_amount = gross_amount - total_fee), -- Airtight DB-level math lock

    -- 3. THE FX AUDIT TRAIL
    usd_normalization_rate NUMERIC(18, 6)        NOT NULL
        CHECK (usd_normalization_rate > 0.000000),

    fx_rate_applied       NUMERIC(18, 6)         NOT NULL
        CHECK (fx_rate_applied > 0.000000),

    destination_amount    NUMERIC(18, 4)         NOT NULL
        CHECK (destination_amount > 0.0000),

    -- 4. EXTERNAL GATEWAYS (Made Nullable for internal P2P transfers)
    funding_gateway       VARCHAR(50),
    gateway_reference     VARCHAR(150) UNIQUE,

    payout_gateway        VARCHAR(50),
    payout_reference      VARCHAR(150) UNIQUE,

    status                VARCHAR(30)            NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),

    idempotency_key       UUID UNIQUE            NOT NULL,

    created_at            TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column()^^

-- 5 LEDGER ENTRIES
CREATE TABLE IF NOT EXISTS ledger_entries
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transaction_id         UUID           NOT NULL
        REFERENCES transactions (id) ON DELETE RESTRICT,

    wallet_id              UUID           NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    -- Categorizes the exact nature of this specific line item
    entry_class            VARCHAR(50)    NOT NULL
        CHECK (entry_class IN ('PRINCIPAL_TRANSFER', 'MARKUP_FEE', 'ROUTING_FEE','FX_CLEARING', 'DEPOSIT', 'WITHDRAWAL', 'REFUND')),

    debit                  NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    credit                 NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    currency               VARCHAR(3)     NOT NULL,

    balance_after          NUMERIC(18, 4) NOT NULL
        CHECK ( balance_after >= 0.0000 ),

    description            VARCHAR(255)   NOT NULL,

    -- ==========================================
    -- PRICING ENGINE AUDIT TRAIL
    -- ==========================================
    routing_pair           VARCHAR(10)    NULL, -- e.g., 'GBP_KES' or 'DEFAULT'

    markup_tiers_applied   VARCHAR(100)   NULL, -- e.g., 'TIER_1, TIER_2'

    usd_baseline_amount    NUMERIC(18, 4) NULL, -- The USD value that justified the tiers at that millisecond

    created_at             TIMESTAMPTZ    DEFAULT CURRENT_TIMESTAMP,

    -- Enforces that both cannot be zero (no empty transactions)
    -- Enforces that if debit > 0, credit must be 0, and vice versa.
    CONSTRAINT chk_debit_credit_exclusive CHECK (
        (debit > 0 AND credit = 0) OR
        (credit > 0 AND debit = 0)
        )
);

-- Trigger Function: Synchronize Wallet Balance
CREATE OR REPLACE FUNCTION process_ledger_entry_and_sync_wallet()
    RETURNS TRIGGER AS
$$
DECLARE
    current_wallet_balance NUMERIC(18, 4);
BEGIN
    -- 1. Lock the specific wallet row to prevent concurrent race conditions
    SELECT balance
    INTO current_wallet_balance
    FROM wallets
    WHERE id = NEW.wallet_id
        FOR UPDATE;

    -- 2. Calculate the new balance based on which column has the value
    IF NEW.debit > 0 THEN
        -- Defensive check: Prevent overdrafts at the ledger level
        IF current_wallet_balance < NEW.debit THEN
            RAISE EXCEPTION 'Insufficient funds in wallet %', NEW.wallet_id;
        END IF;
        NEW.balance_after := current_wallet_balance - NEW.debit;

    ELSIF NEW.credit > 0 THEN
        NEW.balance_after := current_wallet_balance + NEW.credit;

    END IF;

    -- 3. Update the wallet's cached balance
    UPDATE wallets
    SET balance    = NEW.balance_after,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.wallet_id;

    -- 4. Return the NEW row with the injected balance_after so the INSERT completes
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- Attach the sync engine to execute BEFORE the ledger insert finishes
CREATE TRIGGER trigger_process_ledger_entry
    BEFORE INSERT
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION process_ledger_entry_and_sync_wallet();

-- Block Updates and Deletes explicitly at the Database Level
CREATE OR REPLACE FUNCTION block_immutable_ledger_changes()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'Financial Ledger entries are immutable. You cannot modify or delete past logs.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_protect_ledger_updates
    BEFORE UPDATE OR DELETE
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION block_immutable_ledger_changes();

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

