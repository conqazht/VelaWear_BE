-- Contract the customer API after all clients have migrated to principal-bound routes.
-- Match method/path rather than permission name because development repeatable seeds may rename permissions.

WITH legacy_customer_permissions (api_path, http_method) AS (
    VALUES
        ('/api/v1/carts', 'GET'),
        ('/api/v1/carts/items', 'POST'),
        ('/api/v1/carts/items/{id}', 'DELETE'),
        ('/api/v1/orders', 'POST'),
        ('/api/v1/orders/{id}', 'GET'),
        ('/api/v1/payments', 'POST'),
        ('/api/v1/reviews/{id}', 'PUT'),
        ('/api/v1/reviews/{id}', 'DELETE'),
        ('/api/v1/wishlists', 'GET'),
        ('/api/v1/wishlists', 'POST'),
        ('/api/v1/carts', 'POST'),
        ('/api/v1/carts/{id}', 'DELETE'),
        ('/api/v1/user-addresses', 'GET'),
        ('/api/v1/user-addresses/{id}', 'GET'),
        ('/api/v1/user-addresses', 'POST'),
        ('/api/v1/user-addresses/{id}', 'PUT'),
        ('/api/v1/user-addresses/{id}', 'DELETE'),
        ('/api/v1/carts/{id}', 'GET'),
        ('/api/v1/carts/user/{userId}', 'GET'),
        ('/api/v1/orders/code/{orderCode}', 'GET'),
        ('/api/v1/orders/user/{userId}', 'GET'),
        ('/api/v1/orders/{id}/status-histories', 'GET'),
        ('/api/v1/reviews/user/{userId}', 'GET'),
        ('/api/v1/reviews/order/{orderId}', 'GET'),
        ('/api/v1/reviews/order-item/{orderItemId}', 'GET'),
        ('/api/v1/wishlists/{id}', 'GET'),
        ('/api/v1/wishlists/{id}', 'DELETE')
)
DELETE FROM permission_role AS mapping
USING roles AS role, permissions AS permission, legacy_customer_permissions AS legacy
WHERE mapping.role_id = role.id
  AND mapping.permission_id = permission.id
  AND role.name = 'USER'
  AND permission.api_path = legacy.api_path
  AND permission.method = legacy.http_method;
