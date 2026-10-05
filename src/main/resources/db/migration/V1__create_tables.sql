CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    mobile_number VARCHAR(15) NOT NULL UNIQUE,
    pin_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    failed_attempts INT NOT NULL DEFAULT  0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);


CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('CASH_IN', 'TRANSFER_OUT', 'TRANSFER_IN')),
    amount NUMERIC(19,2) NOT NULL CHECK ( amount > 0 ),
    details VARCHAR(255),
    reference_no VARCHAR(30) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT  NOW()
);

CREATE INDEX idx_transactions_user_created ON transactions (user_id, created_at DESC);
