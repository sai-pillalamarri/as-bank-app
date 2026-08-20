INSERT INTO customers (
    id,
    subject,
    first_name,
    last_name,
    status,
    created_at
)
VALUES (
           '11111111-1111-1111-1111-111111111111',
           'customer-local-001',
           'Alex',
           'Morgan',
           'ACTIVE',
           '2026-01-01T00:00:00Z'
       )
    ON CONFLICT (id) DO NOTHING;