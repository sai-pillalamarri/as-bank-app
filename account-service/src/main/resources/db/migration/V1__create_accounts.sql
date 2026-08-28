CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          customer_id UUID NOT NULL,
                          account_number VARCHAR(34) NOT NULL UNIQUE,
                          type VARCHAR(20) NOT NULL,
                          status VARCHAR(20) NOT NULL,
                          balance NUMERIC(19, 2) NOT NULL,
                          currency CHAR(3) NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMPTZ NOT NULL,
                          CONSTRAINT accounts_balance_non_negative
                              CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_customer_id
    ON accounts (customer_id);