INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('VIEW_ADMIN_DASHBOARD', '/api/v1/admin/dashboard/**', 'GET', 'DASHBOARD')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT permission.id, role.id
FROM permissions permission
CROSS JOIN roles role
WHERE permission.name = 'VIEW_ADMIN_DASHBOARD'
  AND role.name IN ('ADMIN', 'MANAGER', 'STAFF')
ON CONFLICT DO NOTHING;
