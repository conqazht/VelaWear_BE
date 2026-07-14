INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('VIEW_MY_CART', '/api/v1/carts/me', 'GET', 'CART'),
    ('REPLACE_MY_CART_ITEMS', '/api/v1/carts/me/items', 'PUT', 'CART')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.name IN ('VIEW_MY_CART', 'REPLACE_MY_CART_ITEMS')
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.name = 'VIEW_COUPONS'
  AND r.name = 'USER'
ON CONFLICT DO NOTHING;
