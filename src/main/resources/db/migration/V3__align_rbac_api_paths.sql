UPDATE permissions
SET api_path = replace(api_path, '/api/', '/api/v1/')
WHERE api_path LIKE '/api/%'
  AND api_path NOT LIKE '/api/v1/%';

ALTER TABLE roles
DROP CONSTRAINT IF EXISTS ck_roles_name;

UPDATE permissions
SET api_path = replace(api_path, '/api/v1/cart', '/api/v1/carts')
WHERE api_path LIKE '/api/v1/cart%';

UPDATE permissions
SET api_path = replace(api_path, '/api/v1/wishlist', '/api/v1/wishlists')
WHERE api_path LIKE '/api/v1/wishlist%';

INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('CREATE_CART', '/api/v1/carts', 'POST', 'CART'),
    ('DELETE_CART', '/api/v1/carts/{id}', 'DELETE', 'CART'),
    ('VIEW_USER_ADDRESSES', '/api/v1/user-addresses', 'GET', 'USER_ADDRESS'),
    ('VIEW_USER_ADDRESS', '/api/v1/user-addresses/{id}', 'GET', 'USER_ADDRESS'),
    ('CREATE_USER_ADDRESS', '/api/v1/user-addresses', 'POST', 'USER_ADDRESS'),
    ('UPDATE_USER_ADDRESS', '/api/v1/user-addresses/{id}', 'PUT', 'USER_ADDRESS'),
    ('DELETE_USER_ADDRESS', '/api/v1/user-addresses/{id}', 'DELETE', 'USER_ADDRESS'),
    ('VIEW_COLORS', '/api/v1/colors', 'GET', 'COLOR'),
    ('CREATE_COLOR', '/api/v1/colors', 'POST', 'COLOR'),
    ('UPDATE_COLOR', '/api/v1/colors/{id}', 'PUT', 'COLOR'),
    ('DELETE_COLOR', '/api/v1/colors/{id}', 'DELETE', 'COLOR'),
    ('VIEW_SIZES', '/api/v1/sizes', 'GET', 'SIZE'),
    ('CREATE_SIZE', '/api/v1/sizes', 'POST', 'SIZE'),
    ('UPDATE_SIZE', '/api/v1/sizes/{id}', 'PUT', 'SIZE'),
    ('DELETE_SIZE', '/api/v1/sizes/{id}', 'DELETE', 'SIZE')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'ADMIN'
  AND p.module IN ('CART', 'USER_ADDRESS', 'COLOR', 'SIZE')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name IN ('MANAGER', 'STAFF')
  AND p.name IN ('VIEW_COLORS', 'VIEW_SIZES')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'USER'
  AND p.name IN (
      'CREATE_CART',
      'DELETE_CART',
      'VIEW_USER_ADDRESSES',
      'VIEW_USER_ADDRESS',
      'CREATE_USER_ADDRESS',
      'UPDATE_USER_ADDRESS',
      'DELETE_USER_ADDRESS'
  )
ON CONFLICT DO NOTHING;
