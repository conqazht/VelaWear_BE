-- Additive self-service permissions for BE-001.
-- Generic permissions intentionally remain until BE-002.

INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('UPDATE_MY_PROFILE', '/api/v1/users/me', 'PUT', 'USER'),
    ('VIEW_MY_ORDERS', '/api/v1/orders/me', 'GET', 'ORDER'),
    ('VIEW_MY_ORDER_BY_CODE', '/api/v1/orders/me/code/{orderCode}', 'GET', 'ORDER'),
    ('VIEW_MY_ORDER', '/api/v1/orders/me/{id}', 'GET', 'ORDER'),
    ('VIEW_MY_ORDER_STATUS_HISTORIES', '/api/v1/orders/me/{id}/status-histories', 'GET', 'ORDER'),
    ('VIEW_MY_USER_ADDRESSES', '/api/v1/user-addresses/me', 'GET', 'USER_ADDRESS'),
    ('CREATE_MY_USER_ADDRESS', '/api/v1/user-addresses/me', 'POST', 'USER_ADDRESS'),
    ('VIEW_MY_USER_ADDRESS', '/api/v1/user-addresses/me/{id}', 'GET', 'USER_ADDRESS'),
    ('UPDATE_MY_USER_ADDRESS', '/api/v1/user-addresses/me/{id}', 'PUT', 'USER_ADDRESS'),
    ('DELETE_MY_USER_ADDRESS', '/api/v1/user-addresses/me/{id}', 'DELETE', 'USER_ADDRESS')
ON CONFLICT (api_path, method) DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE p.name IN (
    'UPDATE_MY_PROFILE',
    'VIEW_MY_ORDERS',
    'VIEW_MY_ORDER_BY_CODE',
    'VIEW_MY_ORDER',
    'VIEW_MY_ORDER_STATUS_HISTORIES',
    'VIEW_MY_USER_ADDRESSES',
    'CREATE_MY_USER_ADDRESS',
    'VIEW_MY_USER_ADDRESS',
    'UPDATE_MY_USER_ADDRESS',
    'DELETE_MY_USER_ADDRESS'
)
  AND r.name IN ('ADMIN', 'MANAGER', 'STAFF', 'USER')
ON CONFLICT DO NOTHING;
