INSERT INTO roles (code, name, description, system_role)
VALUES
    ('SUPER_ADMIN', 'Super Admin', 'Full system access.', TRUE),
    ('ADMIN', 'Admin', 'Manage back-office operations.', TRUE),
    ('STAFF', 'Staff', 'Handle catalog and order operations.', TRUE),
    ('CUSTOMER', 'Customer', 'Default role for storefront customers.', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, name, module, description)
VALUES
    ('USER_READ', 'Read users', 'USER', 'View users and account profile data.'),
    ('USER_WRITE', 'Write users', 'USER', 'Create and update users.'),
    ('ROLE_READ', 'Read roles', 'RBAC', 'View roles and permissions.'),
    ('ROLE_WRITE', 'Write roles', 'RBAC', 'Create, update, and assign roles.'),
    ('CATALOG_READ', 'Read catalog', 'CATALOG', 'View categories, products, and variants.'),
    ('CATALOG_WRITE', 'Write catalog', 'CATALOG', 'Create and update categories, products, and variants.'),
    ('INVENTORY_READ', 'Read inventory', 'INVENTORY', 'View stock levels.'),
    ('INVENTORY_WRITE', 'Write inventory', 'INVENTORY', 'Adjust stock levels.'),
    ('ORDER_READ', 'Read orders', 'ORDER', 'View orders and order items.'),
    ('ORDER_WRITE', 'Write orders', 'ORDER', 'Create and update orders.'),
    ('PAYMENT_READ', 'Read payments', 'PAYMENT', 'View payment records.'),
    ('PAYMENT_WRITE', 'Write payments', 'PAYMENT', 'Create and update payment records.'),
    ('SHIPMENT_READ', 'Read shipments', 'SHIPMENT', 'View shipment records.'),
    ('SHIPMENT_WRITE', 'Write shipments', 'SHIPMENT', 'Create and update shipment records.'),
    ('CUSTOMER_READ', 'Read own customer data', 'CUSTOMER', 'View own customer profile, cart, and orders.'),
    ('CUSTOMER_WRITE', 'Write own customer data', 'CUSTOMER', 'Update own customer profile, cart, and checkout data.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'USER_READ',
    'ROLE_READ',
    'CATALOG_READ',
    'CATALOG_WRITE',
    'INVENTORY_READ',
    'INVENTORY_WRITE',
    'ORDER_READ',
    'ORDER_WRITE',
    'PAYMENT_READ',
    'SHIPMENT_READ',
    'SHIPMENT_WRITE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'CATALOG_READ',
    'INVENTORY_READ',
    'ORDER_READ',
    'ORDER_WRITE',
    'PAYMENT_READ',
    'SHIPMENT_READ',
    'SHIPMENT_WRITE'
)
WHERE r.code = 'STAFF'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'CATALOG_READ',
    'CUSTOMER_READ',
    'CUSTOMER_WRITE',
    'ORDER_WRITE',
    'PAYMENT_WRITE'
)
WHERE r.code = 'CUSTOMER'
ON CONFLICT DO NOTHING;
