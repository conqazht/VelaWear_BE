-- Allow authenticated users to submit the self-service checkout endpoint.
-- Checkout derives the customer from the JWT principal, so it is safe for all
-- authenticated application roles while remaining protected from anonymous use.

INSERT INTO permissions (name, api_path, method, module)
VALUES ('CREATE_CHECKOUT', '/api/v1/checkout', 'POST', 'CHECKOUT')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.api_path = '/api/v1/checkout'
  AND p.method = 'POST'
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;
