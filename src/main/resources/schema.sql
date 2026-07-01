-- V1__init_crosspesa_schema.sql

-- 0. GLOBAL TRIGGER FUNCTION
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 1. USERS
CREATE TABLE IF NOT EXISTS users (
    id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) UNIQUE NOT NULL,

    email_verified BOOLEAN  NOT NULL DEFAULT FALSE,

    password_hash  VARCHAR(255) NOT NULL,

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

    date_of_birth        DATE                NOT NULL,

    id_type              VARCHAR(50)         NOT NULL DEFAULT 'NATIONAL_ID'
        CHECK (id_type IN ('NATIONAL_ID', 'PASSPORT')),

    id_number            VARCHAR(100) UNIQUE NOT NULL,

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
);

CREATE TRIGGER update_users_modtime
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();


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
);

CREATE TRIGGER trigger_update_beneficiaries_timestamp
    BEFORE UPDATE
    ON beneficiaries
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();


-- 3. WALLETS
CREATE TABLE IF NOT EXISTS wallets
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),

    user_id        UUID           NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    currency       VARCHAR(3)     NOT NULL DEFAULT 'KES'
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

    created_at     TIMESTAMPTZ    NOT NULL
                                           DEFAULT CURRENT_TIMESTAMP,

    updated_at     TIMESTAMPTZ    NOT NULL
                                           DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_currency UNIQUE (user_id, currency),

    CONSTRAINT check_valid_reservation CHECK (balance >= locked_balance)
);

CREATE TRIGGER update_wallets_modtime
    BEFORE UPDATE
    ON wallets
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 4. TRANSACTIONS
CREATE TABLE IF NOT EXISTS transactions
(
    id                    UUID PRIMARY KEY             DEFAULT gen_random_uuid(),

    sender_id             UUID                NOT NULL
        REFERENCES users (id) ON DELETE RESTRICT,

    beneficiary_id        UUID                NOT NULL
        REFERENCES beneficiaries (id) ON DELETE RESTRICT,

    source_wallet_id      UUID                NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    destination_wallet_id UUID                NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    source_currency       VARCHAR(3)          NOT NULL,

    destination_currency  VARCHAR(3)          NOT NULL,

    source_amount         DECIMAL(18, 4)
        CHECK (source_amount > 0.000000),

    destination_amount    DECIMAL(18, 4)
        CHECK (destination_amount > 0.000000),

    transfer_fee          NUMERIC(18, 4)
        CHECK (transfer_fee > 0.000000),

    fx_rate_applied       NUMERIC(18, 6)      NOT NULL
        CHECK (fx_rate_applied > 0.000000),

    funding_gateway       VARCHAR(50),

    gateway_reference     VARCHAR(150) UNIQUE NOT NULL,

    payout_gateway        VARCHAR(50),

    payout_reference      VARCHAR(150) UNIQUE NOT NULL,

    status                VARCHAR(30)         NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),

    idempotency_key       UUID UNIQUE         NOT NULL,

    created_at            TIMESTAMPTZ         NOT NULL
                                                       DEFAULT CURRENT_TIMESTAMP,

    updated_at            TIMESTAMPTZ         NOT NULL
                                                       DEFAULT CURRENT_TIMESTAMP

);

CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 5 LEDGER ENTRIES
CREATE TABLE IF NOT EXISTS ledger_entries
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transaction_id UUID           NOT NULL
        REFERENCES transactions (id) ON DELETE RESTRICT,

    wallet_id      UUID           NOT NULL
        REFERENCES wallets (id) ON DELETE RESTRICT,

    entry_type     VARCHAR(50)    NOT NULL
        CHECK ( entry_type IN ('DEBIT', 'CREDIT')),

    currency       VARCHAR(3)     NOT NULL,

    amount         DECIMAL(18, 4) NOT NULL
        CHECK ( amount > 0.0000 ),

    balance_after  DECIMAL(18, 4) NOT NULL -- The O(1) performance lifesaver
        CHECK ( balance_after >= 0.0000 ),

    description    VARCHAR(255)   NOT NULL,

    created_at     TIMESTAMPTZ      DEFAULT CURRENT_TIMESTAMP,

    updated_at     TIMESTAMPTZ    NOT NULL
                                    DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION process_ledger_entry_and_sync_wallet()
    RETURNS TRIGGER AS
$$
DECLARE
    current_wallet_balance DECIMAL(18, 4);
BEGIN
    -- 1. Lock the specific wallet row to prevent concurrent race conditions
    -- This guarantees no other transaction can modify this wallet until this entry completes.
    SELECT balance
    INTO current_wallet_balance
    FROM wallets
    WHERE id = NEW.wallet_id
        FOR UPDATE;

    -- 2. Calculate the new balance based on the entry type
    IF NEW.entry_type = 'DEBIT' THEN
        -- Defensive check: Prevent overdrafts at the ledger level
        IF current_wallet_balance < NEW.amount THEN
            RAISE EXCEPTION 'Insufficient funds in wallet %', NEW.wallet_id;
        END IF;

        NEW.balance_after := current_wallet_balance - NEW.amount;

    ELSIF NEW.entry_type = 'CREDIT' THEN
        NEW.balance_after := current_wallet_balance + NEW.amount;

    ELSE
        RAISE EXCEPTION 'Invalid ledger entry type. Must be DEBIT or CREDIT.';
    END IF;

    -- 3. Update the wallet's cached balance
    UPDATE wallets
    SET balance    = NEW.balance_after,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.wallet_id;

    -- 4. The trigger returns the modified NEW row, which now perfectly contains
    -- the calculated balance_after, allowing the INSERT to finally complete.
    RETURN NEW;
END;
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

CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();


-- 6. EXCHANGE RATES
CREATE TABLE IF NOT EXISTS fx_rates
(
    id                   UUID PRIMARY KEY         DEFAULT gen_random_uuid(),

    source_currency      VARCHAR(3)     NOT NULL,

    provider             VARCHAR(100)   NOT NULL
        CHECK ( provider IN ('Chipper Cash', 'Flutter wave', 'Nium', 'Convera', 'Korapay') ),

    destination_currency VARCHAR(3)     NOT NULL,

    -- The actual rate provided by your liquidity source (e.g., 1 USD = 129.50 KES)
    mid_market_rate      DECIMAL(14, 6) NOT NULL,

    -- The percentage markup your platform charges (e.g., 0.0150 for a 1.5% fee spread)
    markup_percentage    DECIMAL(6, 4)  NOT NULL  DEFAULT 0.0000,

    -- The final calculated conversion rate shown to the consumer
    -- Math: mid_market_rate * (1 - markup_percentage) [for outbound] or * (1 + markup_percentage)
    client_rate          DECIMAL(14, 6) NOT NULL,

    valid_from           TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    expires_at           TIMESTAMPTZ    NOT NULL,
    created_at           TIMESTAMPTZ              DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ              DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON fx_rates
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();


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
);
CREATE TRIGGER update_transactions_modtime
    BEFORE UPDATE
    ON notifications
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();


-- 8. INDEXES
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

