INSERT INTO permissions (name, api_path, method, module)
VALUES ('VIEW_MY_COUPONS', '/api/v1/coupons/me', 'GET', 'COUPON')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.name = 'VIEW_MY_COUPONS'
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;
