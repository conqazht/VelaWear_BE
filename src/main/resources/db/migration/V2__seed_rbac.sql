INSERT INTO roles (name, description)
VALUES
    ('ADMIN', 'Full system administration'),
    ('MANAGER', 'Product, order, coupon, inventory, and report management'),
    ('STAFF', 'Daily order, catalog, and inventory operations'),
    ('USER', 'Customer account')
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description;

INSERT INTO permissions (name, api_path, method, module)
VALUES
    ('VIEW_USERS', '/api/v1/users', 'GET', 'USER'),
    ('VIEW_USER', '/api/v1/users/{id}', 'GET', 'USER'),
    ('CREATE_USER', '/api/v1/users', 'POST', 'USER'),
    ('UPDATE_USER', '/api/v1/users/{id}', 'PUT', 'USER'),
    ('DELETE_USER', '/api/v1/users/{id}', 'DELETE', 'USER'),
    ('ASSIGN_ROLE', '/api/v1/users/{id}/roles', 'PUT', 'RBAC'),

    ('VIEW_ROLES', '/api/v1/roles', 'GET', 'RBAC'),
    ('CREATE_ROLE', '/api/v1/roles', 'POST', 'RBAC'),
    ('UPDATE_ROLE', '/api/v1/roles/{id}', 'PUT', 'RBAC'),
    ('DELETE_ROLE', '/api/v1/roles/{id}', 'DELETE', 'RBAC'),
    ('VIEW_PERMISSIONS', '/api/v1/permissions', 'GET', 'RBAC'),
    ('CREATE_PERMISSION', '/api/v1/permissions', 'POST', 'RBAC'),
    ('UPDATE_PERMISSION', '/api/v1/permissions/{id}', 'PUT', 'RBAC'),
    ('DELETE_PERMISSION', '/api/v1/permissions/{id}', 'DELETE', 'RBAC'),

    ('VIEW_BRANDS', '/api/v1/brands', 'GET', 'BRAND'),
    ('CREATE_BRAND', '/api/v1/brands', 'POST', 'BRAND'),
    ('UPDATE_BRAND', '/api/v1/brands/{id}', 'PUT', 'BRAND'),
    ('DELETE_BRAND', '/api/v1/brands/{id}', 'DELETE', 'BRAND'),

    ('VIEW_CATEGORIES', '/api/v1/categories', 'GET', 'CATEGORY'),
    ('CREATE_CATEGORY', '/api/v1/categories', 'POST', 'CATEGORY'),
    ('UPDATE_CATEGORY', '/api/v1/categories/{id}', 'PUT', 'CATEGORY'),
    ('DELETE_CATEGORY', '/api/v1/categories/{id}', 'DELETE', 'CATEGORY'),

    ('VIEW_PRODUCTS', '/api/v1/products', 'GET', 'PRODUCT'),
    ('VIEW_PRODUCT', '/api/v1/products/{id}', 'GET', 'PRODUCT'),
    ('CREATE_PRODUCT', '/api/v1/products', 'POST', 'PRODUCT'),
    ('UPDATE_PRODUCT', '/api/v1/products/{id}', 'PUT', 'PRODUCT'),
    ('DELETE_PRODUCT', '/api/v1/products/{id}', 'DELETE', 'PRODUCT'),

    ('VIEW_PRODUCT_VARIANTS', '/api/v1/product-variants', 'GET', 'PRODUCT'),
    ('CREATE_PRODUCT_VARIANT', '/api/v1/product-variants', 'POST', 'PRODUCT'),
    ('UPDATE_PRODUCT_VARIANT', '/api/v1/product-variants/{id}', 'PUT', 'PRODUCT'),
    ('DELETE_PRODUCT_VARIANT', '/api/v1/product-variants/{id}', 'DELETE', 'PRODUCT'),

    ('VIEW_CART', '/api/v1/cart', 'GET', 'CART'),
    ('UPDATE_CART', '/api/v1/cart/items', 'POST', 'CART'),
    ('DELETE_CART_ITEM', '/api/v1/cart/items/{id}', 'DELETE', 'CART'),

    ('CREATE_ORDER', '/api/v1/orders', 'POST', 'ORDER'),
    ('VIEW_ORDERS', '/api/v1/orders', 'GET', 'ORDER'),
    ('VIEW_ORDER', '/api/v1/orders/{id}', 'GET', 'ORDER'),
    ('UPDATE_ORDER_STATUS', '/api/v1/orders/{id}/status', 'PATCH', 'ORDER'),

    ('VIEW_PAYMENTS', '/api/v1/payments', 'GET', 'PAYMENT'),
    ('CREATE_PAYMENT', '/api/v1/payments', 'POST', 'PAYMENT'),
    ('UPDATE_PAYMENT', '/api/v1/payments/{id}', 'PATCH', 'PAYMENT'),

    ('VIEW_COUPONS', '/api/v1/coupons', 'GET', 'COUPON'),
    ('CREATE_COUPON', '/api/v1/coupons', 'POST', 'COUPON'),
    ('UPDATE_COUPON', '/api/v1/coupons/{id}', 'PUT', 'COUPON'),
    ('DELETE_COUPON', '/api/v1/coupons/{id}', 'DELETE', 'COUPON'),

    ('VIEW_REVIEWS', '/api/v1/reviews', 'GET', 'REVIEW'),
    ('CREATE_REVIEW', '/api/v1/reviews', 'POST', 'REVIEW'),
    ('UPDATE_REVIEW', '/api/v1/reviews/{id}', 'PUT', 'REVIEW'),
    ('DELETE_REVIEW', '/api/v1/reviews/{id}', 'DELETE', 'REVIEW'),

    ('VIEW_INVENTORY', '/api/v1/inventory-logs', 'GET', 'INVENTORY'),
    ('CREATE_INVENTORY_LOG', '/api/v1/inventory-logs', 'POST', 'INVENTORY'),

    ('VIEW_WISHLIST', '/api/v1/wishlist', 'GET', 'WISHLIST'),
    ('UPDATE_WISHLIST', '/api/v1/wishlist', 'POST', 'WISHLIST')
ON CONFLICT (api_path, method) DO UPDATE
SET
    name = EXCLUDED.name,
    module = EXCLUDED.module;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'MANAGER'
  AND p.module IN ('BRAND', 'CATEGORY', 'PRODUCT', 'ORDER', 'PAYMENT', 'COUPON', 'REVIEW', 'INVENTORY')
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'STAFF'
  AND p.name IN (
      'VIEW_BRANDS',
      'VIEW_CATEGORIES',
      'VIEW_PRODUCTS',
      'VIEW_PRODUCT',
      'VIEW_PRODUCT_VARIANTS',
      'VIEW_ORDERS',
      'VIEW_ORDER',
      'UPDATE_ORDER_STATUS',
      'VIEW_PAYMENTS',
      'VIEW_REVIEWS',
      'VIEW_INVENTORY',
      'CREATE_INVENTORY_LOG'
  )
ON CONFLICT DO NOTHING;

INSERT INTO permission_role (permission_id, role_id)
SELECT p.id, r.id
FROM permissions p
CROSS JOIN roles r
WHERE r.name = 'USER'
  AND p.name IN (
      'VIEW_PRODUCTS',
      'VIEW_PRODUCT',
      'VIEW_CART',
      'UPDATE_CART',
      'DELETE_CART_ITEM',
      'CREATE_ORDER',
      'VIEW_ORDER',
      'CREATE_PAYMENT',
      'CREATE_REVIEW',
      'UPDATE_REVIEW',
      'DELETE_REVIEW',
      'VIEW_WISHLIST',
      'UPDATE_WISHLIST'
  )
ON CONFLICT DO NOTHING;
