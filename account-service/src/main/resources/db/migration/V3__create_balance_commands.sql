CREATE TABLE balance_commands (
                                  command_id UUID PRIMARY KEY,
                                  command_type VARCHAR(20) NOT NULL,
                                  source_account_id UUID,
                                  destination_account_id UUID,
                                  amount NUMERIC(19, 2) NOT NULL,
                                  currency VARCHAR(3) NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  failure_reason VARCHAR(40),
                                  source_balance_after NUMERIC(19, 2),
                                  destination_balance_after NUMERIC(19, 2),
                                  created_at TIMESTAMPTZ NOT NULL,

                                  CONSTRAINT balance_commands_amount_positive
                                      CHECK (amount > 0),

                                  CONSTRAINT balance_commands_currency_length
                                      CHECK (char_length(currency) = 3)
);

-- A rejected command may reference an account that does not exist.
-- Keeping these as identifiers rather than foreign keys lets that result be replayed.
CREATE INDEX idx_balance_commands_source
    ON balance_commands(source_account_id);

CREATE INDEX idx_balance_commands_destination
    ON balance_commands(destination_account_id);