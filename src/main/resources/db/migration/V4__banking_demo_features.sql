ALTER TABLE users ADD COLUMN email VARCHAR(254);

CREATE TABLE demo_cards (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    identifier VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN')),
    daily_limit NUMERIC(19,2) NOT NULL DEFAULT 5000 CHECK (daily_limit >= 0),
    expires_on DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
ALTER TABLE transactions ALTER COLUMN reference_no TYPE VARCHAR(64);
ALTER TABLE transactions ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' CHECK (status = 'COMPLETED');
ALTER TABLE transactions ADD COLUMN card_id UUID REFERENCES demo_cards(id);
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check CHECK (type IN ('CASH_IN','TRANSFER_OUT','TRANSFER_IN','CARD_PURCHASE'));
CREATE INDEX idx_transactions_card_created ON transactions(card_id, created_at DESC) WHERE card_id IS NOT NULL;

CREATE TABLE demo_deposits (
    reference VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    method VARCHAR(24) NOT NULL CHECK (method IN ('GCASH_DEMO','CASH_IN')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    completion_response JSONB
);
CREATE INDEX idx_demo_deposits_user ON demo_deposits(user_id,created_at DESC);
CREATE TABLE qr_payment_requests (
    reference UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('TRANSFER','DEPOSIT')),
    amount NUMERIC(19,2) CHECK (amount > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    consumed_by BIGINT REFERENCES users(id)
);
CREATE TABLE idempotency_operations (
    user_id BIGINT NOT NULL REFERENCES users(id),
    idempotency_key VARCHAR(80) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id,idempotency_key)
);
CREATE FUNCTION prevent_transaction_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Transaction history is immutable' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER immutable_transaction_history BEFORE UPDATE OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION prevent_transaction_mutation();
