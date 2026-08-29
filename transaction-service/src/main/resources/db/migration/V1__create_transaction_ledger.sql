CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              idempotency_key VARCHAR(128) NOT NULL UNIQUE,
                              type VARCHAR(20) NOT NULL,
                              source_account_id UUID,
                              destination_account_id UUID,
                              amount NUMERIC(19, 2) NOT NULL,
                              currency VARCHAR(3) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              failure_reason VARCHAR(40),
                              created_at TIMESTAMPTZ NOT NULL,
                              completed_at TIMESTAMPTZ,

                              CONSTRAINT transactions_amount_positive
                                  CHECK (amount > 0),

                              CONSTRAINT transactions_currency_length
                                  CHECK (char_length(currency) = 3)
);

CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,
                                transaction_id UUID NOT NULL,
                                account_id UUID NOT NULL,
                                direction VARCHAR(10) NOT NULL,
                                amount NUMERIC(19, 2) NOT NULL,
                                currency VARCHAR(3) NOT NULL,
                                created_at TIMESTAMPTZ NOT NULL,

                                CONSTRAINT ledger_entries_transaction_fk
                                    FOREIGN KEY (transaction_id)
                                        REFERENCES transactions(id),

                                CONSTRAINT ledger_entries_amount_positive
                                    CHECK (amount > 0),

                                CONSTRAINT ledger_entries_currency_length
                                    CHECK (char_length(currency) = 3),

                                CONSTRAINT ledger_entries_unique_effect
                                    UNIQUE (
                                            transaction_id,
                                            account_id,
                                            direction
                                        )
);

CREATE INDEX idx_ledger_entries_account_created
    ON ledger_entries(account_id, created_at DESC);

CREATE INDEX idx_transactions_source
    ON transactions(source_account_id);

CREATE INDEX idx_transactions_destination
    ON transactions(destination_account_id);