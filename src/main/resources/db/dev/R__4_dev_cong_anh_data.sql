-- Targeted shop-page fixtures for the seeded customer account.
UPDATE users
SET full_name = CASE
    WHEN EXISTS (
        SELECT 1
        FROM users actual_user
        WHERE (LOWER(TRIM(actual_user.full_name)) LIKE '%công anh%'
               OR LOWER(TRIM(actual_user.full_name)) LIKE '%cong anh%')
          AND actual_user.email <> 'user@velawear.local'
    ) THEN 'Demo Customer'
    ELSE 'Công Anh'
END
WHERE email = 'user@velawear.local';

CREATE TEMP TABLE dev_cong_anh_user ON COMMIT DROP AS
SELECT u.id
FROM users u
WHERE LOWER(TRIM(u.full_name)) LIKE '%công anh%'
   OR LOWER(TRIM(u.full_name)) LIKE '%cong anh%'
   OR u.email = 'user@velawear.local'
ORDER BY
    CASE WHEN u.email = 'user@velawear.local' THEN 1 ELSE 0 END,
    u.id
LIMIT 1;

-- Ensure Công Anh and user@velawear.local accounts have ADMIN role with full permissions
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE r.name = 'ADMIN'
  AND (
      u.email = 'user@velawear.local'
      OR LOWER(TRIM(u.full_name)) LIKE '%công anh%'
      OR LOWER(TRIM(u.full_name)) LIKE '%cong anh%'
  )
ON CONFLICT DO NOTHING;

INSERT INTO coupons (
    code,
    type,
    value,
    min_order_amount,
    max_discount,
    usage_limit,
    used_count,
    start_date,
    end_date,
    status
)
VALUES
    ('CONGANH15', 'PERCENTAGE', 15.00, 500000.00, 150000.00, 200, 0,
     CURRENT_TIMESTAMP - INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '45 days', 'ACTIVE'),
    ('CONGANH80K', 'FIXED_AMOUNT', 80000.00, 750000.00, NULL, 150, 0,
     CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP + INTERVAL '30 days', 'ACTIVE'),
    ('CONGANH20', 'PERCENTAGE', 20.00, 1200000.00, 250000.00, 100, 0,
     CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '21 days', 'ACTIVE')
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
SELECT
    u.id,
    'VW-CONGANH-1001',
    'COMPLETED',
    1917000.00,
    30000.00,
    150000.00,
    1797000.00,
    'Công Anh',
    '0901234567',
    '12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    'VNPAY',
    'PAID'
FROM users u
WHERE u.id = (SELECT id FROM dev_cong_anh_user)
ON CONFLICT (order_code) DO UPDATE
SET
    user_id = EXCLUDED.user_id,
    status = EXCLUDED.status,
    subtotal = EXCLUDED.subtotal,
    shipping_fee = EXCLUDED.shipping_fee,
    discount_amount = EXCLUDED.discount_amount,
    final_amount = EXCLUDED.final_amount,
    receiver_name = EXCLUDED.receiver_name,
    receiver_phone = EXCLUDED.receiver_phone,
    receiver_address = EXCLUDED.receiver_address,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status;

INSERT INTO order_items (
    order_id,
    variant_id,
    product_name,
    variant_name,
    sku,
    image,
    list_price,
    price,
    quantity,
    subtotal,
    status
)
SELECT
    o.id,
    pv.id,
    p.name,
    fixture.variant_name,
    pv.sku,
    fixture.image,
    fixture.price,
    fixture.price,
    fixture.quantity,
    fixture.price * fixture.quantity,
    'CONFIRMED'
FROM orders o
JOIN dev_cong_anh_user target_user ON target_user.id = o.user_id
JOIN (
    VALUES
        ('VW-TEE-BLK-M', 'Black / M', '/uploads/products/essential_cotton_tee_black.png', 219000.00::NUMERIC, 1),
        ('NS-JACKET-PUR-M', 'Purple / M', '/uploads/products/north_structured_blazer_purple.png', 1499000.00::NUMERIC, 1),
        ('SV-TOTE-ORG-OS', 'Orange / One Size', '/uploads/products/studio_leather_tote_orange.png', 199000.00::NUMERIC, 1)
) AS fixture(sku, variant_name, image, price, quantity) ON TRUE
JOIN product_variants pv ON pv.sku = fixture.sku
JOIN products p ON p.id = pv.product_id
WHERE o.order_code = 'VW-CONGANH-1001'
  AND NOT EXISTS (
      SELECT 1
      FROM order_items existing
      WHERE existing.order_id = o.id
        AND existing.sku = fixture.sku
  );

DELETE FROM reviews stale_review
USING order_items oi, orders o
WHERE stale_review.order_item_id = oi.id
  AND oi.order_id = o.id
  AND o.order_code = 'VW-CONGANH-1001'
  AND stale_review.user_id <> o.user_id;

INSERT INTO reviews (user_id, order_item_id, rating, comment)
SELECT
    u.id,
    oi.id,
    fixture.rating,
    fixture.comment
FROM users u
JOIN orders o ON o.user_id = u.id AND o.order_code = 'VW-CONGANH-1001'
JOIN order_items oi ON oi.order_id = o.id
JOIN (
    VALUES
        ('VW-TEE-BLK-M', 5, 'Áo mặc rất thoải mái, chất vải mềm và form vừa vặn.'),
        ('NS-JACKET-PUR-M', 4, 'Áo khoác đứng form, màu đẹp và hoàn thiện tốt.'),
        ('SV-TOTE-ORG-OS', 5, 'Túi rộng, chắc chắn và phối đồ hằng ngày rất tiện.')
) AS fixture(sku, rating, comment) ON fixture.sku = oi.sku
WHERE u.id = (SELECT id FROM dev_cong_anh_user)
ON CONFLICT (user_id, order_item_id) DO UPDATE
SET
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

-- One complete order fixture for every remaining order status.
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
    payment_status,
    created_at,
    updated_at
)
SELECT
    u.id,
    fixture.order_code,
    fixture.status,
    fixture.subtotal,
    fixture.shipping_fee,
    fixture.discount_amount,
    fixture.final_amount,
    'Công Anh',
    '0901234567',
    '12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    fixture.payment_method,
    fixture.payment_status,
    CURRENT_TIMESTAMP - fixture.created_ago,
    CURRENT_TIMESTAMP - fixture.updated_ago
FROM users u
JOIN (
    VALUES
        ('VW-CONGANH-PENDING',   'PENDING',   219000.00::NUMERIC, 30000.00::NUMERIC,      0.00::NUMERIC,  249000.00::NUMERIC, 'COD',           'UNPAID',   INTERVAL '2 hours',  INTERVAL '2 hours'),
        ('VW-CONGANH-CONFIRMED', 'CONFIRMED', 438000.00::NUMERIC, 30000.00::NUMERIC,  30000.00::NUMERIC,  438000.00::NUMERIC, 'VNPAY',         'PAID',     INTERVAL '1 day',   INTERVAL '20 hours'),
        ('VW-CONGANH-SHIPPING',  'SHIPPING', 1499000.00::NUMERIC, 30000.00::NUMERIC, 100000.00::NUMERIC, 1429000.00::NUMERIC, 'MOMO',          'PAID',     INTERVAL '3 days',  INTERVAL '1 day'),
        ('VW-CONGANH-CANCELLED', 'CANCELLED', 199000.00::NUMERIC, 30000.00::NUMERIC,      0.00::NUMERIC,  229000.00::NUMERIC, 'COD',           'FAILED',   INTERVAL '5 days',  INTERVAL '4 days'),
        ('VW-CONGANH-REFUNDED',  'REFUNDED', 1499000.00::NUMERIC, 30000.00::NUMERIC, 149900.00::NUMERIC, 1379100.00::NUMERIC, 'BANK_TRANSFER', 'REFUNDED', INTERVAL '12 days', INTERVAL '2 days')
) AS fixture(
    order_code,
    status,
    subtotal,
    shipping_fee,
    discount_amount,
    final_amount,
    payment_method,
    payment_status,
    created_ago,
    updated_ago
) ON TRUE
WHERE u.id = (SELECT id FROM dev_cong_anh_user)
ON CONFLICT (order_code) DO UPDATE
SET
    user_id = EXCLUDED.user_id,
    status = EXCLUDED.status,
    subtotal = EXCLUDED.subtotal,
    shipping_fee = EXCLUDED.shipping_fee,
    discount_amount = EXCLUDED.discount_amount,
    final_amount = EXCLUDED.final_amount,
    receiver_name = EXCLUDED.receiver_name,
    receiver_phone = EXCLUDED.receiver_phone,
    receiver_address = EXCLUDED.receiver_address,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

INSERT INTO order_items (
    order_id,
    variant_id,
    product_name,
    variant_name,
    sku,
    image,
    list_price,
    price,
    quantity,
    subtotal,
    status
)
SELECT
    o.id,
    pv.id,
    p.name,
    fixture.variant_name,
    pv.sku,
    fixture.image,
    fixture.price,
    fixture.price,
    fixture.quantity,
    fixture.price * fixture.quantity,
    fixture.item_status
FROM orders o
JOIN dev_cong_anh_user target_user ON target_user.id = o.user_id
JOIN (
    VALUES
        ('VW-CONGANH-PENDING',   'VW-TEE-BLK-M',      'Black / M',          '/uploads/products/essential_cotton_tee_black.png',     219000.00::NUMERIC,  1, 'PENDING'),
        ('VW-CONGANH-CONFIRMED', 'VW-TEE-RED-L',      'Red / L',            '/uploads/products/essential_cotton_tee_red.png',       219000.00::NUMERIC,  2, 'CONFIRMED'),
        ('VW-CONGANH-SHIPPING',  'NS-JACKET-PUR-M',   'Purple / M',         '/uploads/products/north_structured_blazer_purple.png', 1499000.00::NUMERIC, 1, 'CONFIRMED'),
        ('VW-CONGANH-CANCELLED', 'SV-TOTE-ORG-OS',    'Orange / One Size',  '/uploads/products/studio_leather_tote_orange.png',     199000.00::NUMERIC,  1, 'CANCELLED'),
        ('VW-CONGANH-REFUNDED',  'NS-JACKET-PUR-M',   'Purple / M',         '/uploads/products/north_structured_blazer_purple.png', 1499000.00::NUMERIC, 1, 'RETURNED')
) AS fixture(order_code, sku, variant_name, image, price, quantity, item_status)
    ON fixture.order_code = o.order_code
JOIN product_variants pv ON pv.sku = fixture.sku
JOIN products p ON p.id = pv.product_id
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items existing
    WHERE existing.order_id = o.id
      AND existing.sku = fixture.sku
);

-- Coupon history for the customer coupon dashboard. The order constraint makes
-- the repeatable migration safe to run after every development restart.
INSERT INTO coupon_usages (
    coupon_id,
    user_id,
    order_id,
    discount_amount,
    used_at
)
SELECT
    c.id,
    o.user_id,
    o.id,
    fixture.discount_amount,
    o.updated_at
FROM orders o
JOIN (
    VALUES
        ('VW-CONGANH-1001',     'CONGANH15', 150000.00::NUMERIC),
        ('VW-CONGANH-REFUNDED', 'CONGANH20', 149900.00::NUMERIC)
) AS fixture(order_code, coupon_code, discount_amount)
    ON fixture.order_code = o.order_code
JOIN coupons c ON c.code = fixture.coupon_code
ON CONFLICT (order_id) DO UPDATE
SET
    coupon_id = EXCLUDED.coupon_id,
    user_id = EXCLUDED.user_id,
    discount_amount = EXCLUDED.discount_amount,
    used_at = EXCLUDED.used_at;

UPDATE coupons c
SET used_count = (
    SELECT COUNT(*)::INTEGER
    FROM coupon_usages usage
    WHERE usage.coupon_id = c.id
)
WHERE c.code IN ('CONGANH15', 'CONGANH20', 'CONGANH80K');

-- Payment records make the payment section of each order detail usable.
INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
SELECT
    o.id,
    fixture.provider,
    fixture.transaction_code,
    o.final_amount,
    fixture.payment_status,
    CASE
        WHEN fixture.payment_status IN ('SUCCESS', 'REFUNDED') THEN o.updated_at
        ELSE NULL
    END
FROM orders o
JOIN (
    VALUES
        ('VW-CONGANH-1001',      'VNPAY',  'VNPAY-CONGANH-1001',      'SUCCESS'),
        ('VW-CONGANH-PENDING',   'COD',    'COD-CONGANH-PENDING',     'PENDING'),
        ('VW-CONGANH-CONFIRMED', 'VNPAY',  'VNPAY-CONGANH-CONFIRMED', 'SUCCESS'),
        ('VW-CONGANH-SHIPPING',  'MOMO',   'MOMO-CONGANH-SHIPPING',   'SUCCESS'),
        ('VW-CONGANH-CANCELLED', 'COD',    'COD-CONGANH-CANCELLED',   'CANCELLED'),
        ('VW-CONGANH-REFUNDED',  'STRIPE', 'STRIPE-CONGANH-REFUNDED', 'REFUNDED')
) AS fixture(order_code, provider, transaction_code, payment_status)
    ON fixture.order_code = o.order_code
ON CONFLICT (transaction_code) DO UPDATE
SET
    order_id = EXCLUDED.order_id,
    provider = EXCLUDED.provider,
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at;

-- Status timelines are displayed by the order-detail API.
INSERT INTO order_status_histories (
    order_id,
    from_status,
    to_status,
    changed_by,
    reason,
    created_at
)
SELECT
    o.id,
    fixture.from_status,
    fixture.to_status,
    staff.id,
    fixture.reason,
    o.created_at + fixture.after_created
FROM orders o
JOIN (
    VALUES
        ('VW-CONGANH-1001',      NULL::VARCHAR, 'PENDING',   'Đơn hàng đã được tạo',                 INTERVAL '0 minutes'),
        ('VW-CONGANH-1001',      'PENDING',     'CONFIRMED', 'Cửa hàng đã xác nhận đơn hàng',       INTERVAL '1 hour'),
        ('VW-CONGANH-1001',      'CONFIRMED',   'SHIPPING',  'Đơn hàng đã được bàn giao vận chuyển', INTERVAL '1 day'),
        ('VW-CONGANH-1001',      'SHIPPING',    'COMPLETED', 'Khách hàng đã nhận được hàng',         INTERVAL '3 days'),
        ('VW-CONGANH-PENDING',   NULL::VARCHAR, 'PENDING',   'Đơn hàng đang chờ xác nhận',           INTERVAL '0 minutes'),
        ('VW-CONGANH-CONFIRMED', NULL::VARCHAR, 'PENDING',   'Đơn hàng đã được tạo',                 INTERVAL '0 minutes'),
        ('VW-CONGANH-CONFIRMED', 'PENDING',     'CONFIRMED', 'Cửa hàng đã xác nhận đơn hàng',       INTERVAL '4 hours'),
        ('VW-CONGANH-SHIPPING',  NULL::VARCHAR, 'PENDING',   'Đơn hàng đã được tạo',                 INTERVAL '0 minutes'),
        ('VW-CONGANH-SHIPPING',  'PENDING',     'CONFIRMED', 'Cửa hàng đã xác nhận đơn hàng',       INTERVAL '3 hours'),
        ('VW-CONGANH-SHIPPING',  'CONFIRMED',   'SHIPPING',  'Đơn vị vận chuyển đã lấy hàng',       INTERVAL '1 day'),
        ('VW-CONGANH-CANCELLED', NULL::VARCHAR, 'PENDING',   'Đơn hàng đã được tạo',                 INTERVAL '0 minutes'),
        ('VW-CONGANH-CANCELLED', 'PENDING',     'CANCELLED', 'Khách hàng yêu cầu hủy đơn',          INTERVAL '1 day'),
        ('VW-CONGANH-REFUNDED',  NULL::VARCHAR, 'PENDING',   'Đơn hàng đã được tạo',                 INTERVAL '0 minutes'),
        ('VW-CONGANH-REFUNDED',  'PENDING',     'CONFIRMED', 'Cửa hàng đã xác nhận đơn hàng',       INTERVAL '2 hours'),
        ('VW-CONGANH-REFUNDED',  'CONFIRMED',   'SHIPPING',  'Đơn hàng đang được vận chuyển',       INTERVAL '1 day'),
        ('VW-CONGANH-REFUNDED',  'SHIPPING',    'COMPLETED', 'Khách hàng đã nhận được hàng',         INTERVAL '3 days'),
        ('VW-CONGANH-REFUNDED',  'COMPLETED',   'REFUNDED',  'Đã hoàn tiền sau khi nhận hàng trả',  INTERVAL '10 days')
) AS fixture(order_code, from_status, to_status, reason, after_created)
    ON fixture.order_code = o.order_code
JOIN users staff ON staff.email = 'staff@velawear.local'
WHERE NOT EXISTS (
    SELECT 1
    FROM order_status_histories existing
    WHERE existing.order_id = o.id
      AND existing.to_status = fixture.to_status
);

-- OAuth accounts are created after Flyway has finished. Move the fixtures as
-- soon as the real Công Anh account appears so reloads keep using the same user.
CREATE OR REPLACE FUNCTION assign_dev_fixtures_to_cong_anh()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.email <> 'user@velawear.local'
       AND (LOWER(TRIM(NEW.full_name)) LIKE '%công anh%'
            OR LOWER(TRIM(NEW.full_name)) LIKE '%cong anh%') THEN
        INSERT INTO user_role (user_id, role_id)
        SELECT NEW.id, r.id
        FROM roles r
        WHERE r.name = 'ADMIN'
        ON CONFLICT DO NOTHING;

        UPDATE orders
        SET user_id = NEW.id,
            receiver_name = NEW.full_name
        WHERE order_code LIKE 'VW-CONGANH-%';

        UPDATE coupon_usages usage
        SET user_id = NEW.id
        FROM orders o
        WHERE usage.order_id = o.id
          AND o.order_code LIKE 'VW-CONGANH-%';

        DELETE FROM reviews r
        USING order_items oi, orders o
        WHERE r.order_item_id = oi.id
          AND oi.order_id = o.id
          AND o.order_code = 'VW-CONGANH-1001';

        INSERT INTO reviews (user_id, order_item_id, rating, comment)
        SELECT
            NEW.id,
            oi.id,
            CASE WHEN oi.sku = 'NS-JACKET-PUR-M' THEN 4 ELSE 5 END,
            CASE oi.sku
                WHEN 'VW-TEE-BLK-M' THEN 'Áo mặc rất thoải mái, chất vải mềm và form vừa vặn.'
                WHEN 'NS-JACKET-PUR-M' THEN 'Áo khoác đứng form, màu đẹp và hoàn thiện tốt.'
                ELSE 'Túi rộng, chắc chắn và phối đồ hằng ngày rất tiện.'
            END
        FROM orders o
        JOIN order_items oi ON oi.order_id = o.id
        WHERE o.order_code = 'VW-CONGANH-1001'
        ON CONFLICT (user_id, order_item_id) DO UPDATE
        SET
            rating = EXCLUDED.rating,
            comment = EXCLUDED.comment;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_assign_dev_fixtures_to_cong_anh ON users;
CREATE TRIGGER trg_assign_dev_fixtures_to_cong_anh
AFTER INSERT OR UPDATE OF full_name ON users
FOR EACH ROW
EXECUTE FUNCTION assign_dev_fixtures_to_cong_anh();
