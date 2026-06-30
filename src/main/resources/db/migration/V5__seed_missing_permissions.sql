-- 1. Correct existing misconfigured permissions
DELETE FROM permissions
WHERE api_path = '/api/v1/payments/{id}' AND method = 'PATCH'
  AND EXISTS (
      SELECT 1 FROM (SELECT id FROM permissions WHERE api_path = '/api/v1/payments/{id}' AND method = 'PUT') AS temp
  );

UPDATE permissions
SET method = 'PUT'
WHERE api_path = '/api/v1/payments/{id}' AND method = 'PATCH';

-- 2. Insert missing permissions
INSERT INTO permissions (name, api_path, method, module)
VALUES
    -- BRAND
    ('VIEW_BRAND', '/api/v1/brands/{id}', 'GET', 'BRAND'),
    
    -- CATEGORY
    ('VIEW_CATEGORY', '/api/v1/categories/{id}', 'GET', 'CATEGORY'),
    
    -- PRODUCT
    ('VIEW_PRODUCT_VARIANT', '/api/v1/product-variants/{id}', 'GET', 'PRODUCT'),
    
    -- CART
    ('VIEW_CART_BY_ID', '/api/v1/carts/{id}', 'GET', 'CART'),
    ('VIEW_CART_BY_USER', '/api/v1/carts/user/{userId}', 'GET', 'CART'),
    
    -- ORDER
    ('VIEW_ORDER_BY_CODE', '/api/v1/orders/code/{orderCode}', 'GET', 'ORDER'),
    ('VIEW_ORDERS_BY_USER', '/api/v1/orders/user/{userId}', 'GET', 'ORDER'),
    ('VIEW_ORDER_STATUS_HISTORIES', '/api/v1/orders/{id}/status-histories', 'GET', 'ORDER'),
    ('UPDATE_ORDER', '/api/v1/orders/{id}', 'PUT', 'ORDER'),
    ('DELETE_ORDER', '/api/v1/orders/{id}', 'DELETE', 'ORDER'),
    
    -- PAYMENT
    ('VIEW_PAYMENT', '/api/v1/payments/{id}', 'GET', 'PAYMENT'),
    ('DELETE_PAYMENT', '/api/v1/payments/{id}', 'DELETE', 'PAYMENT'),
    
    -- COUPON
    ('VIEW_COUPON', '/api/v1/coupons/{id}', 'GET', 'COUPON'),
    
    -- REVIEW
    ('VIEW_REVIEWS_BY_USER', '/api/v1/reviews/user/{userId}', 'GET', 'REVIEW'),
    ('VIEW_REVIEWS_BY_ORDER', '/api/v1/reviews/order/{orderId}', 'GET', 'REVIEW'),
    ('VIEW_REVIEWS_BY_ORDER_ITEM', '/api/v1/reviews/order-item/{orderItemId}', 'GET', 'REVIEW'),
    
    -- COLOR
    ('VIEW_COLOR', '/api/v1/colors/{id}', 'GET', 'COLOR'),
    
    -- SIZE
    ('VIEW_SIZE', '/api/v1/sizes/{id}', 'GET', 'SIZE'),
    
    -- WISHLIST
    ('VIEW_WISHLIST_BY_ID', '/api/v1/wishlists/{id}', 'GET', 'WISHLIST'),
    ('DELETE_WISHLIST', '/api/v1/wishlists/{id}', 'DELETE', 'WISHLIST'),
    
    -- RBAC (ROLE & PERMISSION)
    ('VIEW_ROLE', '/api/v1/roles/{id}', 'GET', 'RBAC'),
    ('VIEW_PERMISSION', '/api/v1/permissions/{id}', 'GET', 'RBAC')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

-- 3. Link new permissions to roles

-- Link ALL permissions (including newly inserted ones) to ADMIN
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Link MANAGER permissions
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'MANAGER'
  AND p.name IN (
      'VIEW_BRAND', 'VIEW_CATEGORY', 'VIEW_PRODUCT_VARIANT',
      'VIEW_ORDER_BY_CODE', 'VIEW_ORDERS_BY_USER', 'VIEW_ORDER_STATUS_HISTORIES', 'UPDATE_ORDER', 'DELETE_ORDER',
      'VIEW_PAYMENT', 'DELETE_PAYMENT',
      'VIEW_COUPON',
      'VIEW_REVIEWS_BY_USER', 'VIEW_REVIEWS_BY_ORDER', 'VIEW_REVIEWS_BY_ORDER_ITEM',
      'VIEW_COLOR', 'VIEW_SIZE'
  )
ON CONFLICT DO NOTHING;

-- Link STAFF permissions
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'STAFF'
  AND p.name IN (
      'VIEW_BRAND', 'VIEW_CATEGORY', 'VIEW_PRODUCT_VARIANT',
      'VIEW_ORDER_BY_CODE', 'VIEW_ORDERS_BY_USER', 'VIEW_ORDER_STATUS_HISTORIES',
      'VIEW_PAYMENT',
      'VIEW_REVIEWS_BY_USER', 'VIEW_REVIEWS_BY_ORDER', 'VIEW_REVIEWS_BY_ORDER_ITEM',
      'VIEW_COLOR', 'VIEW_SIZE'
  )
ON CONFLICT DO NOTHING;

-- Link USER permissions
INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'USER'
  AND p.name IN (
      'VIEW_BRAND', 'VIEW_CATEGORY', 'VIEW_PRODUCT_VARIANT',
      'VIEW_CART_BY_ID', 'VIEW_CART_BY_USER',
      'VIEW_ORDER_BY_CODE', 'VIEW_ORDERS_BY_USER', 'VIEW_ORDER_STATUS_HISTORIES',
      'VIEW_REVIEWS_BY_USER', 'VIEW_REVIEWS_BY_ORDER', 'VIEW_REVIEWS_BY_ORDER_ITEM',
      'VIEW_COLOR', 'VIEW_SIZE',
      'VIEW_WISHLIST_BY_ID', 'DELETE_WISHLIST'
  )
ON CONFLICT DO NOTHING;
