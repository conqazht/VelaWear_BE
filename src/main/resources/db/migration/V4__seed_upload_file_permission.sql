INSERT INTO permissions (name, api_path, method, module)
VALUES ('UPLOAD_FILE', '/api/v1/files', 'POST', 'FILE')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.name = 'UPLOAD_FILE'
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;
