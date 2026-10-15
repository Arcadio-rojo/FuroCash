CREATE TABLE otp_codes (
                           mobile_number VARCHAR(15) PRIMARY KEY,
                           code_hash     VARCHAR(100) NOT NULL,
                           expires_at    TIMESTAMP NOT NULL,
                           attempts      INT NOT NULL DEFAULT 0,
                           last_sent_at  TIMESTAMP NOT NULL
);