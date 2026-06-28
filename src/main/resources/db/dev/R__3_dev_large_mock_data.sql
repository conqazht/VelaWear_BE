-- Repeatable migration to generate ~100 records for each table (excluding users, roles, permissions)

-- 1. Brands (100 records)
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO brands (name, slug, description, status)
        VALUES ('Brand ' || i, 'brand-' || i, 'Description for brand ' || i, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;
END $$;

-- 2. Categories (100 records)
DO $$
DECLARE
    i INT;
    parent_cat_id BIGINT;
BEGIN
    -- Insert parent categories
    FOR i IN 1..10 LOOP
        INSERT INTO categories (name, slug, parent_id, status, sort_order)
        VALUES ('Category ' || i, 'category-' || i, NULL, 'ACTIVE', i)
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert subcategories
    FOR i IN 11..100 LOOP
        SELECT id INTO parent_cat_id FROM categories WHERE parent_id IS NULL ORDER BY RANDOM() LIMIT 1;
        INSERT INTO categories (name, slug, parent_id, status, sort_order)
        VALUES ('Category ' || i, 'category-' || i, parent_cat_id, 'ACTIVE', i)
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;
END $$;

-- 3. Colors are intentionally limited to the curated palette from R__1/R__2:
-- Black, Red, Yellow, Purple, Orange.

-- 4. Sizes are intentionally limited to XS, S, M, L, XL, XXL and 35..45.

-- 5. Products (100 records)
DO $$
DECLARE
    i INT;
    cat_id BIGINT;
    br_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO cat_id FROM categories ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES ('Product ' || i, 'product-' || i, 'Description for product ' || i, cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;
END $$;

-- 5.1 Vietnamese catalog translations
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT
    id,
    'vi',
    CASE slug
        WHEN 'men' THEN 'Nam'
        WHEN 'women' THEN 'Nữ'
        WHEN 't-shirts' THEN 'Áo thun'
        WHEN 'dresses' THEN 'Đầm'
        WHEN 'accessories' THEN 'Phụ kiện'
        WHEN 'outerwear' THEN 'Áo khoác ngoài'
        WHEN 'jackets' THEN 'Áo khoác'
        ELSE regexp_replace(name, '^Category ', 'Danh mục ')
    END,
    CASE slug
        WHEN 'men' THEN 'nam'
        WHEN 'women' THEN 'nu'
        WHEN 't-shirts' THEN 'ao-thun'
        WHEN 'dresses' THEN 'dam'
        WHEN 'accessories' THEN 'phu-kien'
        WHEN 'outerwear' THEN 'ao-khoac-ngoai'
        WHEN 'jackets' THEN 'ao-khoac'
        ELSE regexp_replace(slug, '^category-', 'danh-muc-')
    END,
    CASE slug
        WHEN 'men' THEN 'Danh mục thời trang nam.'
        WHEN 'women' THEN 'Danh mục thời trang nữ.'
        WHEN 't-shirts' THEN 'Áo thun mặc hằng ngày.'
        WHEN 'dresses' THEN 'Đầm nhẹ nhàng cho nhiều dịp.'
        WHEN 'accessories' THEN 'Phụ kiện hoàn thiện trang phục.'
        WHEN 'outerwear' THEN 'Trang phục khoác ngoài tiện dụng.'
        WHEN 'jackets' THEN 'Áo khoác cho nhịp sống đô thị.'
        ELSE 'Danh mục sản phẩm VelaWear.'
    END,
    CASE slug
        WHEN 'men' THEN 'Thời trang nam'
        WHEN 'women' THEN 'Thời trang nữ'
        WHEN 't-shirts' THEN 'Áo thun'
        WHEN 'dresses' THEN 'Đầm'
        WHEN 'accessories' THEN 'Phụ kiện'
        WHEN 'outerwear' THEN 'Áo khoác ngoài'
        WHEN 'jackets' THEN 'Áo khoác'
        ELSE regexp_replace(name, '^Category ', 'Danh mục ')
    END,
    CASE slug
        WHEN 'men' THEN 'Khám phá các thiết kế thời trang nam từ VelaWear.'
        WHEN 'women' THEN 'Khám phá các thiết kế thời trang nữ từ VelaWear.'
        WHEN 't-shirts' THEN 'Các mẫu áo thun dễ phối cho ngày thường.'
        WHEN 'dresses' THEN 'Các mẫu đầm nhẹ, thoải mái và tinh tế.'
        WHEN 'accessories' THEN 'Phụ kiện tối giản cho trang phục hằng ngày.'
        WHEN 'outerwear' THEN 'Trang phục khoác ngoài gọn gàng và thực dụng.'
        WHEN 'jackets' THEN 'Các mẫu áo khoác tiện dụng cho thời tiết thay đổi.'
        ELSE 'Khám phá danh mục sản phẩm VelaWear.'
    END
FROM categories
ON CONFLICT (category_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description;

INSERT INTO product_translations (
    product_id,
    locale_code,
    name,
    slug,
    short_description,
    description,
    material,
    care_instruction,
    seo_title,
    seo_description
)
SELECT
    id,
    'vi',
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Áo thun cotton cơ bản'
        WHEN 'urban-linen-dress' THEN 'Đầm linen đô thị'
        WHEN 'north-utility-jacket' THEN 'Áo khoác tiện ích North'
        WHEN 'studio-canvas-tote' THEN 'Túi tote canvas Studio'
        ELSE regexp_replace(name, '^Product ', 'Sản phẩm ')
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'ao-thun-cotton-co-ban'
        WHEN 'urban-linen-dress' THEN 'dam-linen-do-thi'
        WHEN 'north-utility-jacket' THEN 'ao-khoac-tien-ich-north'
        WHEN 'studio-canvas-tote' THEN 'tui-tote-canvas-studio'
        ELSE regexp_replace(slug, '^product-', 'san-pham-')
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Áo thun cotton mềm, dễ mặc mỗi ngày.'
        WHEN 'urban-linen-dress' THEN 'Đầm linen nhẹ cho những ngày ấm.'
        WHEN 'north-utility-jacket' THEN 'Áo khoác nhẹ nhiều túi tiện dụng.'
        WHEN 'studio-canvas-tote' THEN 'Túi tote canvas bền cho nhu cầu hằng ngày.'
        ELSE 'Thiết kế tối giản cho tủ đồ hằng ngày.'
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Áo thun cotton mềm với phom thoải mái, phù hợp mặc hằng ngày.'
        WHEN 'urban-linen-dress' THEN 'Đầm linen thoáng nhẹ, phù hợp cho những ngày nắng ấm.'
        WHEN 'north-utility-jacket' THEN 'Áo khoác utility nhẹ với nhiều túi rộng rãi và phom linh hoạt.'
        WHEN 'studio-canvas-tote' THEN 'Túi tote canvas chắc chắn, đủ rộng cho các vật dụng thường ngày.'
        ELSE 'Sản phẩm VelaWear được thiết kế tối giản, dễ phối và thoải mái.'
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Cotton'
        WHEN 'urban-linen-dress' THEN 'Linen'
        WHEN 'north-utility-jacket' THEN 'Cotton pha'
        WHEN 'studio-canvas-tote' THEN 'Canvas'
        ELSE NULL
    END,
    CASE slug
        WHEN 'studio-canvas-tote' THEN 'Lau sạch bằng khăn ẩm.'
        ELSE 'Giặt máy nước lạnh.'
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Áo thun cotton cơ bản'
        WHEN 'urban-linen-dress' THEN 'Đầm linen đô thị'
        WHEN 'north-utility-jacket' THEN 'Áo khoác tiện ích North'
        WHEN 'studio-canvas-tote' THEN 'Túi tote canvas Studio'
        ELSE regexp_replace(name, '^Product ', 'Sản phẩm ')
    END,
    CASE slug
        WHEN 'essential-cotton-tee' THEN 'Áo thun cotton mềm, phom thoải mái cho ngày thường.'
        WHEN 'urban-linen-dress' THEN 'Đầm linen nhẹ, thoáng và dễ mặc trong ngày ấm.'
        WHEN 'north-utility-jacket' THEN 'Áo khoác utility nhẹ với nhiều túi tiện dụng.'
        WHEN 'studio-canvas-tote' THEN 'Túi tote canvas bền, tối giản cho nhu cầu hằng ngày.'
        ELSE 'Thiết kế tối giản cho tủ đồ hằng ngày.'
    END
FROM products
ON CONFLICT (product_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    short_description = EXCLUDED.short_description,
    description = EXCLUDED.description,
    material = EXCLUDED.material,
    care_instruction = EXCLUDED.care_instruction,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description;

-- 6. Product Variants (100 records)
DO $$
DECLARE
    i INT;
    prod_id BIGINT;
    col_id BIGINT;
    sz_id BIGINT;
BEGIN
    FOR i IN 1..150 LOOP
        SELECT id INTO prod_id FROM products ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO col_id FROM colors ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO sz_id FROM sizes ORDER BY RANDOM() LIMIT 1;
        IF NOT EXISTS (
            SELECT 1 FROM product_variants
            WHERE product_id = prod_id AND color_id = col_id AND size_id = sz_id
        ) THEN
            INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
            VALUES (prod_id, 'SKU-' || i || '-' || FLOOR(RANDOM() * 1000000)::INT, 100000.00 + (i * 10000), NULL, 50, col_id, sz_id, 'ACTIVE')
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 7. Product Images (100 records)
DO $$
DECLARE
    i INT;
    prod_id BIGINT;
    var_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO prod_id FROM products ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO var_id FROM product_variants WHERE product_id = prod_id ORDER BY RANDOM() LIMIT 1;
        INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
        VALUES (prod_id, var_id, '/images/dev/products/img_' || i || '.png', i % 2 = 0, i)
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- 8. Product Attributes (100 records)
DO $$
DECLARE
    i INT;
    prod_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO prod_id FROM products ORDER BY RANDOM() LIMIT 1;
        INSERT INTO product_attributes (product_id, name, value)
        VALUES (prod_id, 'Attribute ' || i, 'Value ' || i)
        ON CONFLICT (product_id, name) DO NOTHING;
    END LOOP;
END $$;

-- 9. User Addresses (100 records)
DO $$
DECLARE
    i INT;
    u_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        IF u_id IS NOT NULL THEN
            INSERT INTO user_addresses (user_id, receiver_name, phone, province, district, ward, address_detail, is_default)
            VALUES (u_id, 'Receiver ' || i, '09' || LPAD(i::text, 8, '0'), 'Province ' || i, 'District ' || i, 'Ward ' || i, 'Address Detail ' || i, FALSE)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 10. Carts (Ensure all users have a cart)
INSERT INTO carts (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

-- 11. Cart Items (100 records)
DO $$
DECLARE
    i INT;
    c_id BIGINT;
    v_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO c_id FROM carts ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO v_id FROM product_variants ORDER BY RANDOM() LIMIT 1;
        IF c_id IS NOT NULL AND v_id IS NOT NULL THEN
            INSERT INTO cart_items (cart_id, variant_id, quantity)
            VALUES (c_id, v_id, (i % 5) + 1)
            ON CONFLICT (cart_id, variant_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 12. Coupons (100 records)
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO coupons (code, type, value, min_order_amount, max_discount, usage_limit, used_count, start_date, end_date, status)
        VALUES ('COUPON' || i, 'PERCENTAGE', 10.00, 100000.00, 50000.00, 100, 0, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '30 days', 'ACTIVE')
        ON CONFLICT (code) DO NOTHING;
    END LOOP;
END $$;

-- 13. Orders (100 records)
DO $$
DECLARE
    i INT;
    u_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        IF u_id IS NOT NULL THEN
            INSERT INTO orders (
                user_id, order_code, status, subtotal, shipping_fee, discount_amount, final_amount,
                receiver_name, receiver_phone, receiver_address, payment_method, payment_status
            )
            VALUES (
                u_id, 'VW-MOCK-' || (1000 + i), 'COMPLETED', 200000.00, 30000.00, 0.00, 230000.00,
                'Customer ' || i, '09' || LPAD(i::text, 8, '0'), 'Mock Address ' || i, 'COD', 'PAID'
            )
            ON CONFLICT (order_code) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 14. Order Items (100 records)
DO $$
DECLARE
    i INT;
    ord_id BIGINT;
    v_id BIGINT;
    v_sku VARCHAR(100);
    p_name VARCHAR(150);
BEGIN
    FOR i IN 1..120 LOOP
        SELECT id INTO ord_id FROM orders ORDER BY RANDOM() LIMIT 1;
        SELECT id, sku, (SELECT name FROM products WHERE id = product_id) INTO v_id, v_sku, p_name FROM product_variants ORDER BY RANDOM() LIMIT 1;
        IF ord_id IS NOT NULL AND v_id IS NOT NULL THEN
            INSERT INTO order_items (order_id, variant_id, product_name, variant_name, sku, image, price, quantity, subtotal, status)
            VALUES (ord_id, v_id, p_name, 'Variant ' || i, v_sku, '/images/dev/product.jpg', 200000.00, 1, 200000.00, 'CONFIRMED')
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 15. Payments (100 records)
DO $$
DECLARE
    i INT;
    ord_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO ord_id FROM orders o WHERE NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id) ORDER BY RANDOM() LIMIT 1;
        IF ord_id IS NOT NULL THEN
            INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
            VALUES (ord_id, 'COD', 'TX-COD-' || i, 230000.00, 'SUCCESS', CURRENT_TIMESTAMP)
            ON CONFLICT (transaction_code) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 16. Payment Transactions (100 records)
DO $$
DECLARE
    i INT;
    pay_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO pay_id FROM payments ORDER BY RANDOM() LIMIT 1;
        IF pay_id IS NOT NULL THEN
            INSERT INTO payment_transactions (payment_id, transaction_code, status, gateway_response)
            VALUES (pay_id, 'TX-GATEWAY-' || i, 'SUCCESS', '{"status":"success"}')
            ON CONFLICT (transaction_code) WHERE transaction_code IS NOT NULL DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 17. Coupon Usages (100 records)
DO $$
DECLARE
    i INT;
    cp_id BIGINT;
    u_id BIGINT;
    ord_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO cp_id FROM coupons ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO ord_id FROM orders o WHERE NOT EXISTS (SELECT 1 FROM coupon_usages cu WHERE cu.order_id = o.id) ORDER BY RANDOM() LIMIT 1;
        IF cp_id IS NOT NULL AND u_id IS NOT NULL AND ord_id IS NOT NULL THEN
            INSERT INTO coupon_usages (coupon_id, user_id, order_id, discount_amount)
            VALUES (cp_id, u_id, ord_id, 10000.00)
            ON CONFLICT (order_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 18. Reviews (100 records)
DO $$
DECLARE
    i INT;
    u_id BIGINT;
    oi_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO oi_id FROM order_items oi WHERE NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_item_id = oi.id) ORDER BY RANDOM() LIMIT 1;
        IF u_id IS NOT NULL AND oi_id IS NOT NULL THEN
            INSERT INTO reviews (user_id, order_item_id, rating, comment)
            VALUES (u_id, oi_id, (i % 5) + 1, 'Mock review comment number ' || i)
            ON CONFLICT (user_id, order_item_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 19. Review Images (100 records)
DO $$
DECLARE
    i INT;
    rev_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO rev_id FROM reviews ORDER BY RANDOM() LIMIT 1;
        IF rev_id IS NOT NULL THEN
            INSERT INTO review_images (review_id, image)
            VALUES (rev_id, '/images/dev/reviews/rev_img_' || i || '.jpg')
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 20. Inventory Logs (100 records)
DO $$
DECLARE
    i INT;
    v_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO v_id FROM product_variants ORDER BY RANDOM() LIMIT 1;
        IF v_id IS NOT NULL THEN
            INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
            VALUES (v_id, 10, 'IMPORT', 'Mock inventory import ' || i)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 21. Order Status Histories (100 records)
DO $$
DECLARE
    i INT;
    ord_id BIGINT;
    u_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO ord_id FROM orders ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        IF ord_id IS NOT NULL AND u_id IS NOT NULL THEN
            INSERT INTO order_status_histories (order_id, from_status, to_status, changed_by, reason)
            VALUES (ord_id, 'PENDING', 'COMPLETED', u_id, 'Status change ' || i)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 22. Wishlists (100 records)
DO $$
DECLARE
    i INT;
    u_id BIGINT;
    prod_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        SELECT id INTO prod_id FROM products ORDER BY RANDOM() LIMIT 1;
        IF u_id IS NOT NULL AND prod_id IS NOT NULL THEN
            INSERT INTO wishlists (user_id, product_id)
            VALUES (u_id, prod_id)
            ON CONFLICT (user_id, product_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;
