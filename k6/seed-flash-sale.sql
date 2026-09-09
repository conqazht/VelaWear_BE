-- ==============================================================================
-- Seed Script for High-Concurrency Checkout & Flash Sale Stress Test (k6)
-- Target: 200 Concurrent Buyers, 20 Items in Stock
-- ==============================================================================

-- 1. Seed 200 test buyer accounts (Password is Password123!)
INSERT INTO users (full_name, email, password, birth_date, gender)
SELECT 
    'Test Buyer ' || i,
    'buyer_' || i || '@test.local',
    '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS',
    DATE '1995-01-01',
    'OTHER'
FROM generate_series(1, 200) i
ON CONFLICT (email) DO NOTHING;

-- 2. Assign USER role to all test buyers
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE r.name = 'USER' AND u.email LIKE 'buyer_%@test.local'
ON CONFLICT DO NOTHING;

-- 3. Set Flash Sale Stock for Variant 1 (20 items in stock)
UPDATE product_variants
SET stock_quantity = 20,
    status = 'ACTIVE'
WHERE id = 1;

-- 4. Clean up any stale cart items for all test buyers
DELETE FROM cart_items
WHERE cart_id IN (
    SELECT c.id FROM carts c
    JOIN users u ON u.id = c.user_id
    WHERE u.email LIKE 'buyer_%@test.local'
);

-- 5. Output status
SELECT id, sku, stock_quantity, status
FROM product_variants
WHERE id = 1;

SELECT count(*) AS seeded_buyers_count
FROM users
WHERE email LIKE 'buyer_%@test.local';
