ALTER TABLE accounts
ALTER COLUMN currency TYPE VARCHAR(3)
    USING TRIM(currency)::VARCHAR(3);

ALTER TABLE accounts
    ADD CONSTRAINT accounts_currency_length
        CHECK (char_length(currency) = 3);