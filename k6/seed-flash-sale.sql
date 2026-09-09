-- Helper SQL script to set up a Flash Sale scenario
-- Target Variant ID: 1
-- Initial Stock: 10 items

UPDATE product_variants
SET stock_quantity = 10,
    status = 'ACTIVE'
WHERE id = 1;

-- Clean up any stale cart items for user@velawear.local
DELETE FROM cart_items
WHERE cart_id IN (
    SELECT c.id FROM carts c
    JOIN users u ON u.id = c.user_id
    WHERE u.email = 'user@velawear.local'
);

SELECT id, sku, stock_quantity, status
FROM product_variants
WHERE id = 1;
