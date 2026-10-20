ALTER TABLE demo_deposits DROP CONSTRAINT demo_deposits_method_check;
ALTER TABLE demo_deposits ADD CONSTRAINT demo_deposits_method_check CHECK (method IN ('GCASH_DEMO','CASH_IN','CASH_DEMO','BANK_TRANSFER_DEMO','EWALLET_DEMO','OTHER_DEMO'));
ALTER TABLE demo_deposits ADD COLUMN note VARCHAR(150) NOT NULL DEFAULT '';
CREATE TABLE transaction_receipts (
 user_id BIGINT NOT NULL REFERENCES users(id),
 reference VARCHAR(64) NOT NULL,
 transaction_id BIGINT NOT NULL UNIQUE REFERENCES transactions(id),
 receipt JSONB NOT NULL CHECK (receipt->>'status' = 'COMPLETED'),
 PRIMARY KEY(user_id,reference)
);
CREATE TRIGGER immutable_receipts BEFORE UPDATE OR DELETE ON transaction_receipts FOR EACH ROW EXECUTE FUNCTION prevent_transaction_mutation();
CREATE TABLE transfer_reviews (
 reference VARCHAR(64) PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 recipient_id BIGINT NOT NULL REFERENCES users(id),
 recipient_name VARCHAR(255) NOT NULL,
 amount NUMERIC(19,2) NOT NULL CHECK (amount>0),
 fee NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (fee=0),
 note VARCHAR(140) NOT NULL,
 qr_reference VARCHAR(64) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMP NOT NULL DEFAULT NOW()+INTERVAL '5 minutes',
 consumed_at TIMESTAMP
);
CREATE INDEX idx_transfer_reviews_owner ON transfer_reviews(user_id,reference);
