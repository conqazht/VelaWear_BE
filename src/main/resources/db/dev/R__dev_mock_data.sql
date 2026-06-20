INSERT INTO users (full_name, email, password, birth_date, avatar, gender)
VALUES
    ('VelaWear Admin', 'admin@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '1995-01-10', NULL, 'OTHER'),
    ('VelaWear Staff', 'staff@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '1998-05-20', NULL, 'FEMALE'),
    ('Demo Customer', 'user@velawear.local', '$2a$12$jpmg9X/G2khv3X5405kl8erpqLDljNHfuACZGw4vlbcYlyBeLEQqS', DATE '2000-09-15', NULL, 'MALE')
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
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@velawear.local'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'STAFF'
WHERE u.email = 'staff@velawear.local'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'USER'
WHERE u.email = 'user@velawear.local'
ON CONFLICT DO NOTHING;

INSERT INTO brands (name, slug, description, status)
VALUES
    ('VelaWear', 'velawear', 'VelaWear in-house apparel label.', 'ACTIVE'),
    ('Urban Thread', 'urban-thread', 'Modern citywear brand.', 'ACTIVE')
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = EXCLUDED.status;

INSERT INTO categories (name, slug, parent_id, status, sort_order)
VALUES
    ('Men', 'men', NULL, 'ACTIVE', 1),
    ('Women', 'women', NULL, 'ACTIVE', 2)
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO categories (name, slug, parent_id, status, sort_order)
SELECT 'T-Shirts', 't-shirts', c.id, 'ACTIVE', 1
FROM categories c
WHERE c.slug = 'men'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO categories (name, slug, parent_id, status, sort_order)
SELECT 'Dresses', 'dresses', c.id, 'ACTIVE', 1
FROM categories c
WHERE c.slug = 'women'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO colors (name, hex_code, sort_order)
VALUES
    ('Black', '#000000', 1),
    ('White', '#FFFFFF', 2),
    ('Navy', '#1F3A5F', 3)
ON CONFLICT (name) DO UPDATE
SET
    hex_code = EXCLUDED.hex_code,
    sort_order = EXCLUDED.sort_order;

INSERT INTO sizes (name, sort_order)
VALUES
    ('S', 1),
    ('M', 2),
    ('L', 3),
    ('XL', 4)
ON CONFLICT (name) DO UPDATE
SET sort_order = EXCLUDED.sort_order;

INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'Essential Cotton Tee',
       'essential-cotton-tee',
       'Soft daily cotton T-shirt with relaxed fit.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 't-shirts'
  AND b.slug = 'velawear'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'Urban Linen Dress',
       'urban-linen-dress',
       'Light linen dress for warm days.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 'dresses'
  AND b.slug = 'urban-thread'
ON CONFLICT (slug) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
SELECT p.id, 'VW-TEE-BLK-M', 249000.00, 219000.00, 80, c.id, s.id, 'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'essential-cotton-tee'
  AND c.name = 'Black'
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
SELECT p.id, 'VW-TEE-WHT-L', 249000.00, NULL, 55, c.id, s.id, 'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'essential-cotton-tee'
  AND c.name = 'White'
  AND s.name = 'L'
ON CONFLICT (sku) DO UPDATE
SET
    price = EXCLUDED.price,
    sale_price = EXCLUDED.sale_price,
    stock_quantity = EXCLUDED.stock_quantity,
    color_id = EXCLUDED.color_id,
    size_id = EXCLUDED.size_id,
    status = EXCLUDED.status;

INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
SELECT p.id, 'UT-DRESS-NVY-S', 699000.00, 649000.00, 30, c.id, s.id, 'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'urban-linen-dress'
  AND c.name = 'Navy'
  AND s.name = 'S'
ON CONFLICT (sku) DO UPDATE
SET
    price = EXCLUDED.price,
    sale_price = EXCLUDED.sale_price,
    stock_quantity = EXCLUDED.stock_quantity,
    color_id = EXCLUDED.color_id,
    size_id = EXCLUDED.size_id,
    status = EXCLUDED.status;

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, NULL, '/images/dev/essential-cotton-tee.jpg', TRUE, 1
FROM products p
WHERE p.slug = 'essential-cotton-tee'
  AND NOT EXISTS (
      SELECT 1
      FROM product_images pi
      WHERE pi.product_id = p.id
        AND pi.variant_id IS NULL
        AND pi.is_thumbnail = TRUE
  );

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, NULL, '/images/dev/urban-linen-dress.jpg', TRUE, 1
FROM products p
WHERE p.slug = 'urban-linen-dress'
  AND NOT EXISTS (
      SELECT 1
      FROM product_images pi
      WHERE pi.product_id = p.id
        AND pi.variant_id IS NULL
        AND pi.is_thumbnail = TRUE
  );

INSERT INTO product_attributes (product_id, name, value)
SELECT p.id, 'Material', '100% cotton'
FROM products p
WHERE p.slug = 'essential-cotton-tee'
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

INSERT INTO product_attributes (product_id, name, value)
SELECT p.id, 'Material', 'Linen blend'
FROM products p
WHERE p.slug = 'urban-linen-dress'
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

INSERT INTO user_addresses (user_id, receiver_name, phone, province, district, ward, address_detail, is_default)
SELECT u.id, 'Demo Customer', '0900000000', 'Ho Chi Minh', 'District 1', 'Ben Nghe', '123 Le Loi', TRUE
FROM users u
WHERE u.email = 'user@velawear.local'
  AND NOT EXISTS (
      SELECT 1
      FROM user_addresses ua
      WHERE ua.user_id = u.id
        AND ua.is_default = TRUE
  );

INSERT INTO carts (user_id)
SELECT u.id
FROM users u
WHERE u.email = 'user@velawear.local'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO cart_items (cart_id, variant_id, quantity)
SELECT c.id, pv.id, 1
FROM carts c
JOIN users u ON u.id = c.user_id
JOIN product_variants pv ON pv.sku = 'VW-TEE-WHT-L'
WHERE u.email = 'user@velawear.local'
ON CONFLICT (cart_id, variant_id) DO UPDATE
SET quantity = EXCLUDED.quantity;

INSERT INTO coupons (code, type, value, min_order_amount, max_discount, usage_limit, used_count, start_date, end_date, status)
VALUES
    ('WELCOME10', 'PERCENTAGE', 10.00, 300000.00, 100000.00, 1000, 0, CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '90 days', 'ACTIVE'),
    ('FREESHIP50', 'FIXED_AMOUNT', 50000.00, 500000.00, NULL, 500, 0, CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '90 days', 'ACTIVE')
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
    user_id,
    order_code,
    status,
    subtotal,
    shipping_fee,
    discount_amount,
    final_amount,
    receiver_name,
    receiver_phone,
    receiver_address,
    payment_method,
    payment_status
)
SELECT u.id,
       'VW-DEV-1001',
       'COMPLETED',
       438000.00,
       30000.00,
       46800.00,
       421200.00,
       'Demo Customer',
       '0900000000',
       '123 Le Loi, Ben Nghe, District 1, Ho Chi Minh',
       'VNPAY',
       'PAID'
FROM users u
WHERE u.email = 'user@velawear.local'
ON CONFLICT (order_code) DO UPDATE
SET
    status = EXCLUDED.status,
    subtotal = EXCLUDED.subtotal,
    shipping_fee = EXCLUDED.shipping_fee,
    discount_amount = EXCLUDED.discount_amount,
    final_amount = EXCLUDED.final_amount,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status;

INSERT INTO order_items (order_id, variant_id, product_name, variant_name, sku, image, price, quantity, subtotal, status)
SELECT o.id,
       pv.id,
       p.name,
       'Black / M',
       pv.sku,
       '/images/dev/essential-cotton-tee.jpg',
       219000.00,
       2,
       438000.00,
       'CONFIRMED'
FROM orders o
JOIN product_variants pv ON pv.sku = 'VW-TEE-BLK-M'
JOIN products p ON p.id = pv.product_id
WHERE o.order_code = 'VW-DEV-1001'
  AND NOT EXISTS (
      SELECT 1
      FROM order_items oi
      WHERE oi.order_id = o.id
        AND oi.sku = pv.sku
  );

INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
SELECT o.id, 'VNPAY', 'VNPAY-DEV-1001', 421200.00, 'SUCCESS', CURRENT_TIMESTAMP
FROM orders o
WHERE o.order_code = 'VW-DEV-1001'
ON CONFLICT (transaction_code) DO UPDATE
SET
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at;

INSERT INTO payment_transactions (payment_id, transaction_code, status, gateway_response)
SELECT p.id,
       'VNPAY-DEV-1001-AUTH',
       'SUCCESS',
       '{"provider":"VNPAY","environment":"dev","message":"Mock payment success"}'::jsonb
FROM payments p
WHERE p.transaction_code = 'VNPAY-DEV-1001'
  AND NOT EXISTS (
      SELECT 1
      FROM payment_transactions pt
      WHERE pt.transaction_code = 'VNPAY-DEV-1001-AUTH'
  );

INSERT INTO coupon_usages (coupon_id, user_id, order_id, discount_amount)
SELECT c.id, u.id, o.id, 46800.00
FROM coupons c
JOIN users u ON u.email = 'user@velawear.local'
JOIN orders o ON o.order_code = 'VW-DEV-1001'
WHERE c.code = 'WELCOME10'
ON CONFLICT (order_id) DO UPDATE
SET
    coupon_id = EXCLUDED.coupon_id,
    user_id = EXCLUDED.user_id,
    discount_amount = EXCLUDED.discount_amount;

UPDATE coupons c
SET used_count = usage.used_count
FROM (
    SELECT coupon_id, COUNT(*)::INTEGER AS used_count
    FROM coupon_usages
    GROUP BY coupon_id
) usage
WHERE c.id = usage.coupon_id;

INSERT INTO reviews (user_id, product_id, order_item_id, rating, comment)
SELECT u.id,
       p.id,
       oi.id,
       5,
       'Soft fabric and clean fit. Good demo product.'
FROM users u
JOIN products p ON p.slug = 'essential-cotton-tee'
JOIN order_items oi ON oi.product_name = p.name
JOIN orders o ON o.id = oi.order_id
WHERE u.email = 'user@velawear.local'
  AND o.order_code = 'VW-DEV-1001'
ON CONFLICT (user_id, order_item_id) DO UPDATE
SET
    product_id = EXCLUDED.product_id,
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

INSERT INTO review_images (review_id, image)
SELECT r.id, '/images/dev/reviews/essential-cotton-tee-review.jpg'
FROM reviews r
JOIN users u ON u.id = r.user_id
JOIN products p ON p.id = r.product_id
WHERE u.email = 'user@velawear.local'
  AND p.slug = 'essential-cotton-tee'
  AND NOT EXISTS (
      SELECT 1
      FROM review_images ri
      WHERE ri.review_id = r.id
        AND ri.image = '/images/dev/reviews/essential-cotton-tee-review.jpg'
  );

INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
SELECT pv.id, 80, 'IMPORT', 'Initial dev stock import'
FROM product_variants pv
WHERE pv.sku = 'VW-TEE-BLK-M'
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_logs il
      WHERE il.variant_id = pv.id
        AND il.type = 'IMPORT'
        AND il.reason = 'Initial dev stock import'
  );

INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
SELECT pv.id, -2, 'ORDER', 'Dev order VW-DEV-1001'
FROM product_variants pv
WHERE pv.sku = 'VW-TEE-BLK-M'
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_logs il
      WHERE il.variant_id = pv.id
        AND il.type = 'ORDER'
        AND il.reason = 'Dev order VW-DEV-1001'
  );

INSERT INTO order_status_histories (order_id, from_status, to_status, changed_by, reason)
SELECT o.id, NULL, 'PENDING', u.id, 'Dev order created'
FROM orders o
JOIN users u ON u.email = 'staff@velawear.local'
WHERE o.order_code = 'VW-DEV-1001'
  AND NOT EXISTS (
      SELECT 1
      FROM order_status_histories osh
      WHERE osh.order_id = o.id
        AND osh.to_status = 'PENDING'
  );

INSERT INTO order_status_histories (order_id, from_status, to_status, changed_by, reason)
SELECT o.id, 'PENDING', 'COMPLETED', u.id, 'Dev order completed'
FROM orders o
JOIN users u ON u.email = 'staff@velawear.local'
WHERE o.order_code = 'VW-DEV-1001'
  AND NOT EXISTS (
      SELECT 1
      FROM order_status_histories osh
      WHERE osh.order_id = o.id
        AND osh.to_status = 'COMPLETED'
  );

INSERT INTO wishlists (user_id, product_id)
SELECT u.id, p.id
FROM users u
JOIN products p ON p.slug = 'urban-linen-dress'
WHERE u.email = 'user@velawear.local'
ON CONFLICT (user_id, product_id) DO NOTHING;
