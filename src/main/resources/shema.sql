-- automatic updated_at trigger function
    CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
    BEGIN
        NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
    END;
    $$ LANGUAGE plpgsql;

-- 1. Table: users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) UNIQUE NOT NULL,

    password_hash VARCHAR(255) NOT NULL,

    first_name VARCHAR(100) NOT NULL,

    last_name VARCHAR(100) NOT NULL,

    phone_number VARCHAR(20) UNIQUE NOT NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'USER'
         CHECK (role IN ('USER','MERCHANT','ADMIN')),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','SUSPENDED','LOCKED')),

    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (kyc_status IN ('PENDING','APPROVED','REJECTED')),

    kyc_level SMALLINT NOT NULL DEFAULT 1 CHECK (kyc_level IN (1, 2, 3)),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
CREATE TRIGGER update_users_modtime BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 2. Table: wallets
CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    currency VARCHAR(3) NOT NULL
         CHECK (length(currency) = 3),

    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000
         CHECK (balance >= 0.0000),

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
        CHECK (status IN ('ACTIVE','FROZEN')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_currency UNIQUE (user_id, currency)
    );
CREATE TRIGGER update_users_modtime BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 3. Table: transactions
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    sender_wallet_id UUID NOT NULL
       REFERENCES wallets(id) ON DELETE RESTRICT,

    recipient_type VARCHAR(30) NOT NULL,

    recipient_reference VARCHAR(255) NOT NULL,

    source_amount NUMERIC(18, 4) NOT NULL
       CHECK (source_amount > 0.0000),

    destination_amount NUMERIC(18, 4) NOT NULL
        CHECK (destination_amount > 0.0000),

    exchange_rate NUMERIC(18, 6) NOT NULL
      CHECK (exchange_rate > 0.000000),

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
       CHECK (status IN('PENDING','PROCESSING','COMPLETED','FAILED','CANCELLED')),

    idempotency_key VARCHAR(255) UNIQUE NOT NULL,

    mpesa_receipt_no VARCHAR(50) UNIQUE NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_internal_transfer_has_receiver CHECK (
       (recipient_type = 'INTERNAL' AND receiver_wallet_id IS NOT NULL) OR
       (recipient_type != 'INTERNAL')
    )
    );
CREATE TRIGGER update_transactions_modtime BEFORE UPDATE ON transactions FOR EACH ROW EXECUTE FUNCTION update_modified_column();


-- 4. Table: exchange_rates
CREATE TABLE IF NOT EXISTS exchange_rates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    source_currency VARCHAR(3) NOT NULL,

    provider VARCHAR(100) NOT NULL,

    destination_currency VARCHAR(3) NOT NULL,

    rate NUMERIC(18, 6) NOT NULL
        CHECK (rate > 0.000000),

    fetched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );


-- 5. Table: notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    title VARCHAR(150) NOT NULL,

    message TEXT NOT NULL,

    notification_type VARCHAR(20) DEFAULT 'SMS',
        CHECK (notification_type IN ('EMAIL', 'SMS','IN_APP')),

    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD'
        CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );


--- INDEXES
-- Optimization Index for real-time exchange rate checks
CREATE INDEX IF NOT EXISTS idx_ex_rates_lookup
    ON exchange_rates (source_currency, destination_currency, fetched_at DESC);

CREATE INDEX idx_transactions_sender ON transactions(sender_wallet_id);

CREATE INDEX idx_transactions_receiver ON transactions(receiver_wallet_id)
    WHERE receiver_wallet_id IS NOT NULL;

-- High-performance Partial Index for active system operations
CREATE INDEX idx_transactions_active_status ON transactions(status)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_notifications_user ON notifications(user_id);




