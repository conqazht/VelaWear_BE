INSERT INTO users (full_name, email, password, birth_date, avatar, gender)
VALUES
    ('Linh Nguyen', 'linh@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '1997-03-12', NULL, 'FEMALE'),
    ('Minh Tran', 'minh@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '1993-11-02', NULL, 'MALE'),
    ('Ops Manager', 'manager@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '1990-07-25', NULL, 'OTHER')
ON CONFLICT (email) DO UPDATE
SET
    full_name = EXCLUDED.full_name,
    password = EXCLUDED.password,
    birth_date = EXCLUDED.birth_date,
    avatar = EXCLUDED.avatar,
    gender = EXCLUDED.gender;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'USER'
WHERE u.email IN ('linh@velawear.local', 'minh@velawear.local')
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'MANAGER'
WHERE u.email = 'manager@velawear.local'
ON CONFLICT DO NOTHING;

INSERT INTO brands (name, slug, description, status)
VALUES
    ('North Stitch', 'north-stitch', 'Clean everyday outerwear and basics.', 'ACTIVE'),
    ('Studio V', 'studio-v', 'Minimal premium capsule pieces.', 'ACTIVE')
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status;

INSERT INTO categories (name, slug, parent_id, status, sort_order)
VALUES
    ('Accessories', 'accessories', NULL, 'ACTIVE', 3),
    ('Outerwear', 'outerwear', NULL, 'ACTIVE', 4)
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO categories (name, slug, parent_id, status, sort_order)
SELECT 'Jackets', 'jackets', c.id, 'ACTIVE', 1
FROM categories c
WHERE c.slug = 'outerwear'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO colors (name, hex_code, sort_order)
VALUES
    ('Purple', '#7B2CBF', 5),
    ('Orange', '#F97316', 6)
ON CONFLICT (name) DO UPDATE
SET
    hex_code = EXCLUDED.hex_code,
    sort_order = EXCLUDED.sort_order;

INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'North Structured Blazer',
       'north-utility-jacket',
       'Structured tailored blazer with a clean single-button silhouette.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 'jackets'
  AND b.slug = 'north-stitch'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'Studio Leather Tote',
       'studio-canvas-tote',
       'Premium pebbled-leather tote for daily carry.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 'accessories'
  AND b.slug = 'studio-v'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
SELECT p.id, 'NS-JACKET-PUR-M', 1199000.00, 999000.00, 24, c.id, s.id, 'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'north-utility-jacket'
  AND c.name = 'Purple'
  AND s.name = 'M'
ON CONFLICT (sku) DO UPDATE
SET
    price = EXCLUDED.price,
    sale_price = EXCLUDED.sale_price,
    stock_quantity = EXCLUDED.stock_quantity,
    color_id = EXCLUDED.color_id,
    size_id = EXCLUDED.size_id,
    status = EXCLUDED.status;

INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
SELECT p.id, 'SV-TOTE-ORG-OS', 329000.00, NULL, 120, c.id, s.id, 'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'studio-canvas-tote'
  AND c.name = 'Orange'
  AND s.name = 'ONE SIZE'
ON CONFLICT (sku) DO UPDATE
SET
    price = EXCLUDED.price,
    sale_price = EXCLUDED.sale_price,
    stock_quantity = EXCLUDED.stock_quantity,
    color_id = EXCLUDED.color_id,
    size_id = EXCLUDED.size_id,
    status = EXCLUDED.status;

DELETE FROM product_images WHERE product_id IN (
    SELECT id FROM products WHERE slug IN ('north-utility-jacket', 'studio-canvas-tote')
);

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/north_structured_blazer_purple.png', TRUE, 1
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'NS-JACKET-PUR-M'
WHERE p.slug = 'north-utility-jacket';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/north_structured_blazer_purple_front.png', FALSE, 2
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'NS-JACKET-PUR-M'
WHERE p.slug = 'north-utility-jacket';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/north_structured_blazer_purple_details.png', FALSE, 3
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'NS-JACKET-PUR-M'
WHERE p.slug = 'north-utility-jacket';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/studio_leather_tote_orange.png', TRUE, 1
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'SV-TOTE-ORG-OS'
WHERE p.slug = 'studio-canvas-tote';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/studio_leather_tote_orange_gallery.png', FALSE, 2
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'SV-TOTE-ORG-OS'
WHERE p.slug = 'studio-canvas-tote';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/studio_leather_tote_orange_detail.png', FALSE, 3
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'SV-TOTE-ORG-OS'
WHERE p.slug = 'studio-canvas-tote';

INSERT INTO product_attributes (product_id, name, value)
SELECT p.id, 'Care', 'Machine wash cold'
FROM products p
WHERE p.slug = 'north-utility-jacket'
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

INSERT INTO product_attributes (product_id, name, value)
SELECT p.id, 'Capacity', '18 liters'
FROM products p
WHERE p.slug = 'studio-canvas-tote'
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

INSERT INTO user_addresses (user_id, receiver_name, phone, province, ward, address_detail, is_default)
SELECT u.id, u.full_name, '0911111111', 'Ha Noi', 'Hang Trong', '45 Trang Tien', TRUE
FROM users u
WHERE u.email = 'linh@velawear.local'
  AND NOT EXISTS (SELECT 1 FROM user_addresses ua WHERE ua.user_id = u.id AND ua.is_default = TRUE);

INSERT INTO user_addresses (user_id, receiver_name, phone, province, ward, address_detail, is_default)
SELECT u.id, u.full_name, '0922222222', 'Da Nang', 'Thach Thang', '18 Bach Dang', TRUE
FROM users u
WHERE u.email = 'minh@velawear.local'
  AND NOT EXISTS (SELECT 1 FROM user_addresses ua WHERE ua.user_id = u.id AND ua.is_default = TRUE);

INSERT INTO carts (user_id)
SELECT u.id
FROM users u
WHERE u.email IN ('linh@velawear.local', 'minh@velawear.local')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO cart_items (cart_id, variant_id, quantity)
SELECT c.id, pv.id, 1
FROM carts c
JOIN users u ON u.id = c.user_id
JOIN product_variants pv ON pv.sku = 'NS-JACKET-PUR-M'
WHERE u.email = 'linh@velawear.local'
ON CONFLICT (cart_id, variant_id) DO UPDATE
SET quantity = EXCLUDED.quantity;

INSERT INTO cart_items (cart_id, variant_id, quantity)
SELECT c.id, pv.id, 2
FROM carts c
JOIN users u ON u.id = c.user_id
JOIN product_variants pv ON pv.sku = 'SV-TOTE-ORG-OS'
WHERE u.email = 'minh@velawear.local'
ON CONFLICT (cart_id, variant_id) DO UPDATE
SET quantity = EXCLUDED.quantity;

INSERT INTO coupons (code, type, value, min_order_amount, max_discount, usage_limit, used_count, start_date, end_date, status)
VALUES
    ('JACKET15', 'PERCENTAGE', 15.00, 800000.00, 180000.00, 200, 0, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '60 days', 'ACTIVE'),
    ('TOTE30K', 'FIXED_AMOUNT', 30000.00, 250000.00, NULL, 300, 0, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '60 days', 'ACTIVE')
ON CONFLICT (code) DO UPDATE
SET
    type = EXCLUDED.type,
    value = EXCLUDED.value,
    min_order_amount = EXCLUDED.min_order_amount,
    max_discount = EXCLUDED.max_discount,
    usage_limit = EXCLUDED.usage_limit,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    status = EXCLUDED.status;

INSERT INTO orders (
    user_id, order_code, status, subtotal, shipping_fee, discount_amount, final_amount,
    receiver_name, receiver_phone, receiver_address, payment_method, payment_status
)
SELECT u.id, 'VW-DEV-1002', 'COMPLETED', 999000.00, 0.00, 149850.00, 849150.00,
       'Linh Nguyen', '0911111111', '45 Trang Tien, Hoan Kiem, Ha Noi', 'COD', 'PAID'
FROM users u
WHERE u.email = 'linh@velawear.local'
ON CONFLICT (order_code) DO UPDATE
SET status = EXCLUDED.status,
    subtotal = EXCLUDED.subtotal,
    shipping_fee = EXCLUDED.shipping_fee,
    discount_amount = EXCLUDED.discount_amount,
    final_amount = EXCLUDED.final_amount,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status;

INSERT INTO orders (
    user_id, order_code, status, subtotal, shipping_fee, discount_amount, final_amount,
    receiver_name, receiver_phone, receiver_address, payment_method, payment_status
)
SELECT u.id, 'VW-DEV-1003', 'PENDING', 658000.00, 30000.00, 30000.00, 658000.00,
       'Minh Tran', '0922222222', '18 Bach Dang, Hai Chau, Da Nang', 'MOMO', 'UNPAID'
FROM users u
WHERE u.email = 'minh@velawear.local'
ON CONFLICT (order_code) DO UPDATE
SET status = EXCLUDED.status,
    subtotal = EXCLUDED.subtotal,
    shipping_fee = EXCLUDED.shipping_fee,
    discount_amount = EXCLUDED.discount_amount,
    final_amount = EXCLUDED.final_amount,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status;

INSERT INTO order_items (order_id, variant_id, product_name, variant_name, sku, image, price, quantity, subtotal, status)
SELECT o.id, pv.id, p.name, 'Purple / M', pv.sku, '/uploads/products/north_structured_blazer_purple.png', 999000.00, 1, 999000.00, 'CONFIRMED'
FROM orders o
JOIN product_variants pv ON pv.sku = 'NS-JACKET-PUR-M'
JOIN products p ON p.id = pv.product_id
WHERE o.order_code = 'VW-DEV-1002'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.sku = pv.sku);

INSERT INTO order_items (order_id, variant_id, product_name, variant_name, sku, image, price, quantity, subtotal, status)
SELECT o.id, pv.id, p.name, 'Orange / One Size', pv.sku, '/uploads/products/studio_leather_tote_orange.png', 329000.00, 2, 658000.00, 'PENDING'
FROM orders o
JOIN product_variants pv ON pv.sku = 'SV-TOTE-ORG-OS'
JOIN products p ON p.id = pv.product_id
WHERE o.order_code = 'VW-DEV-1003'
  AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id AND oi.sku = pv.sku);

INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
SELECT o.id, 'COD', 'COD-DEV-1002', 849150.00, 'SUCCESS', CURRENT_TIMESTAMP
FROM orders o
WHERE o.order_code = 'VW-DEV-1002'
ON CONFLICT (transaction_code) DO UPDATE
SET amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at;

INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
SELECT o.id, 'MOMO', 'MOMO-DEV-1003', 658000.00, 'PENDING', NULL
FROM orders o
WHERE o.order_code = 'VW-DEV-1003'
ON CONFLICT (transaction_code) DO UPDATE
SET amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at;

INSERT INTO coupon_usages (coupon_id, user_id, order_id, discount_amount)
SELECT c.id, u.id, o.id, 149850.00
FROM coupons c
JOIN users u ON u.email = 'linh@velawear.local'
JOIN orders o ON o.order_code = 'VW-DEV-1002'
WHERE c.code = 'JACKET15'
ON CONFLICT (order_id) DO UPDATE
SET coupon_id = EXCLUDED.coupon_id,
    user_id = EXCLUDED.user_id,
    discount_amount = EXCLUDED.discount_amount;

INSERT INTO reviews (user_id, order_item_id, rating, comment)
SELECT u.id, oi.id, 4, 'Good structure and nice pockets. Slightly roomy fit.'
FROM users u
JOIN orders o ON o.user_id = u.id AND o.order_code = 'VW-DEV-1002'
JOIN order_items oi ON oi.order_id = o.id AND oi.sku = 'NS-JACKET-PUR-M'
WHERE u.email = 'linh@velawear.local'
ON CONFLICT (user_id, order_item_id) DO UPDATE
SET rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

INSERT INTO review_images (review_id, image)
SELECT r.id, '/uploads/reviews/north-utility-jacket-review.png'
FROM reviews r
JOIN order_items oi ON oi.id = r.order_item_id
WHERE oi.sku = 'NS-JACKET-PUR-M'
  AND NOT EXISTS (
      SELECT 1 FROM review_images ri
      WHERE ri.review_id = r.id
        AND ri.image = '/uploads/reviews/north-utility-jacket-review.png'
  );

INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
SELECT pv.id, 24, 'IMPORT', 'Additional dev seed stock import'
FROM product_variants pv
WHERE pv.sku = 'NS-JACKET-PUR-M'
  AND NOT EXISTS (
      SELECT 1 FROM inventory_logs il
      WHERE il.variant_id = pv.id
        AND il.type = 'IMPORT'
        AND il.reason = 'Additional dev seed stock import'
  );

INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
SELECT pv.id, -1, 'ORDER', 'Dev order VW-DEV-1002'
FROM product_variants pv
WHERE pv.sku = 'NS-JACKET-PUR-M'
  AND NOT EXISTS (
      SELECT 1 FROM inventory_logs il
      WHERE il.variant_id = pv.id
        AND il.type = 'ORDER'
        AND il.reason = 'Dev order VW-DEV-1002'
  );

INSERT INTO order_status_histories (order_id, from_status, to_status, changed_by, reason)
SELECT o.id, NULL, 'PENDING', u.id, 'Dev order created'
FROM orders o
JOIN users u ON u.email = 'manager@velawear.local'
WHERE o.order_code = 'VW-DEV-1002'
  AND NOT EXISTS (
      SELECT 1 FROM order_status_histories osh
      WHERE osh.order_id = o.id AND osh.to_status = 'PENDING'
  );

INSERT INTO order_status_histories (order_id, from_status, to_status, changed_by, reason)
SELECT o.id, 'PENDING', 'COMPLETED', u.id, 'Dev order completed'
FROM orders o
JOIN users u ON u.email = 'manager@velawear.local'
WHERE o.order_code = 'VW-DEV-1002'
  AND NOT EXISTS (
      SELECT 1 FROM order_status_histories osh
      WHERE osh.order_id = o.id AND osh.to_status = 'COMPLETED'
  );

INSERT INTO wishlists (user_id, product_id)
SELECT u.id, p.id
FROM users u
JOIN products p ON p.slug IN ('north-utility-jacket', 'studio-canvas-tote')
WHERE u.email = 'linh@velawear.local'
ON CONFLICT (user_id, product_id) DO NOTHING;

UPDATE coupons c
SET used_count = usage.used_count
FROM (
    SELECT coupon_id, COUNT(*)::INTEGER AS used_count
    FROM coupon_usages
    GROUP BY coupon_id
) usage
WHERE c.id = usage.coupon_id;
