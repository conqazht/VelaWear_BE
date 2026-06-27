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

-- 3. Colors (100 records)
DO $$
DECLARE
    i INT;
    hex_val VARCHAR(7);
BEGIN
    FOR i IN 1..100 LOOP
        hex_val := '#' || TO_HEX((i * 123456) % 16777215);
        IF LENGTH(hex_val) < 7 THEN
            hex_val := hex_val || REPEAT('0', 7 - LENGTH(hex_val));
        END IF;
        INSERT INTO colors (name, hex_code, sort_order)
        VALUES ('Color ' || i, hex_val, i)
        ON CONFLICT (name) DO NOTHING;
    END LOOP;
END $$;

-- 4. Sizes (100 records)
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..100 LOOP
        INSERT INTO sizes (name, sort_order)
        VALUES ('Size ' || i, i)
        ON CONFLICT (name) DO NOTHING;
    END LOOP;
END $$;

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
