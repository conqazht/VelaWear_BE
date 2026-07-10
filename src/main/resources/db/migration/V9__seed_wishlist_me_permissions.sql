-- Seed RBAC permissions for the /wishlists/me customer-facing endpoints.
-- These were missing, causing 403 for all authenticated users on the
-- self-service wishlist toggle (POST/DELETE /api/v1/wishlists/me/{productId})
-- and the wishlist listing (GET /api/v1/wishlists/me).

INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('VIEW_MY_WISHLISTS',  '/api/v1/wishlists/me',            'GET',    'WISHLIST'),
    ('CREATE_MY_WISHLIST', '/api/v1/wishlists/me/{productId}', 'POST',   'WISHLIST'),
    ('DELETE_MY_WISHLIST', '/api/v1/wishlists/me/{productId}', 'DELETE', 'WISHLIST')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name   = EXCLUDED.name,
    module = EXCLUDED.module;

-- Grant all three to every role (they are self-scoped via @AuthenticationPrincipal)

-- ADMIN
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'ADMIN'
  AND p.name IN ('VIEW_MY_WISHLISTS', 'CREATE_MY_WISHLIST', 'DELETE_MY_WISHLIST')
ON CONFLICT DO NOTHING;

-- MANAGER
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'MANAGER'
  AND p.name IN ('VIEW_MY_WISHLISTS', 'CREATE_MY_WISHLIST', 'DELETE_MY_WISHLIST')
ON CONFLICT DO NOTHING;

-- STAFF
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'STAFF'
  AND p.name IN ('VIEW_MY_WISHLISTS', 'CREATE_MY_WISHLIST', 'DELETE_MY_WISHLIST')
ON CONFLICT DO NOTHING;

-- USER (customer)
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'USER'
  AND p.name IN ('VIEW_MY_WISHLISTS', 'CREATE_MY_WISHLIST', 'DELETE_MY_WISHLIST')
ON CONFLICT DO NOTHING;
