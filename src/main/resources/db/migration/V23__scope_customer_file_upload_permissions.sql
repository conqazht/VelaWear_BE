-- Scope customer upload access to the self-avatar endpoint.
-- Generic POST /api/v1/files remains available to operator roles seeded by V4.

INSERT INTO permissions (name, api_path, method, module)
VALUES ('UPDATE_MY_AVATAR', '/api/v1/files/avatar', 'PUT', 'FILE')
ON CONFLICT (api_path, method) DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.api_path = '/api/v1/files/avatar'
  AND p.method = 'PUT'
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;

DELETE FROM permission_role AS mapping
USING roles AS role, permissions AS permission
WHERE mapping.role_id = role.id
  AND mapping.permission_id = permission.id
  AND role.name = 'USER'
  AND permission.api_path = '/api/v1/files'
  AND permission.method = 'POST';
