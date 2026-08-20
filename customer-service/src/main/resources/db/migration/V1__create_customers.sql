CREATE TABLE customers (
                           id UUID PRIMARY KEY,
                           subject VARCHAR(255) NOT NULL UNIQUE,
                           first_name VARCHAR(100) NOT NULL,
                           last_name VARCHAR(100) NOT NULL,
                           status VARCHAR(20) NOT NULL,
                           created_at TIMESTAMPTZ NOT NULL
);