/*
 * Catalog reconciliation moved to R__3_dev_catalog_products.sql. Keeping this
 * historical cleanup disabled prevents the large feature seed from deleting
 * the complete size runs created for the four original storefront products.
 *
 * Remove variants left by older random dev seeds from the four stable fixtures.
-- Keeping only the declared SKUs prevents one product from retaining a mixture
-- of apparel, numeric shoe, and accessory size systems after a repeatable rerun.
DELETE FROM product_images WHERE variant_id IN (
    SELECT pv.id
    FROM product_variants pv
    JOIN products p ON p.id = pv.product_id
    WHERE p.slug IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
      AND pv.sku NOT IN ('VW-TEE-BLK-M', 'VW-TEE-RED-L', 'UT-DRESS-YLW-S', 'NS-JACKET-PUR-M', 'SV-TOTE-ORG-OS')
);

DELETE FROM cart_items WHERE variant_id IN (
    SELECT pv.id
    FROM product_variants pv
    JOIN products p ON p.id = pv.product_id
    WHERE p.slug IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
      AND pv.sku NOT IN ('VW-TEE-BLK-M', 'VW-TEE-RED-L', 'UT-DRESS-YLW-S', 'NS-JACKET-PUR-M', 'SV-TOTE-ORG-OS')
);

DELETE FROM inventory_logs WHERE variant_id IN (
    SELECT pv.id
    FROM product_variants pv
    JOIN products p ON p.id = pv.product_id
    WHERE p.slug IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
      AND pv.sku NOT IN ('VW-TEE-BLK-M', 'VW-TEE-RED-L', 'UT-DRESS-YLW-S', 'NS-JACKET-PUR-M', 'SV-TOTE-ORG-OS')
);

DELETE FROM product_variants pv
USING products p
WHERE p.id = pv.product_id
  AND p.slug IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
  AND pv.sku NOT IN ('VW-TEE-BLK-M', 'VW-TEE-RED-L', 'UT-DRESS-YLW-S', 'NS-JACKET-PUR-M', 'SV-TOTE-ORG-OS');
*/

-- Do not broadly delete rows that are not part of this fixture. Repeatable dev
-- migrations may run after an Admin has created content or after Sale/order
-- history references a variant. Stable fixture rows below are reconciled by
-- natural keys; unrelated development data is intentionally preserved.

-- 1. Brands (Add 6 more premium brands to make exactly 10 brands total)
INSERT INTO brands (name, slug, description, status)
VALUES
    ('Atelier Sand', 'atelier-sand', 'Refined sand-washed linen and silks.', 'ACTIVE'),
    ('Tailor Espresso', 'tailor-espresso', 'Premium structure and tailoring in dark tones.', 'ACTIVE'),
    ('Linen Gold', 'linen-gold', 'Premium organic flax textiles.', 'ACTIVE'),
    ('Drape Terracotta', 'drape-terracotta', 'Fluid silhouettes and warm clay tones.', 'ACTIVE'),
    ('Minimalist Club', 'minimalist-club', 'Essential monochrome staples.', 'ACTIVE'),
    ('Pacific Loom', 'pacific-loom', 'Lightweight coastal knits and organic cottons.', 'ACTIVE')
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name, description = EXCLUDED.description, status = EXCLUDED.status;

-- 2. Categories (Seed the 7 specific categories requested)
INSERT INTO categories (name, slug, parent_id, status, sort_order)
VALUES
    ('Shirts & Tops', 'ao', NULL, 'ACTIVE', 1),
    ('Pants & Trousers', 'quan', NULL, 'ACTIVE', 2),
    ('Skirts', 'vay', NULL, 'ACTIVE', 3),
    ('Dresses', 'dam', NULL, 'ACTIVE', 4),
    ('Jackets & Coats', 'ao-khoac', NULL, 'ACTIVE', 5),
    ('Shoes', 'giay', NULL, 'ACTIVE', 6),
    ('Accessories', 'phu-kien', NULL, 'ACTIVE', 7)
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name, parent_id = EXCLUDED.parent_id, status = EXCLUDED.status, sort_order = EXCLUDED.sort_order;

-- 2.1 Category Translations (Vietnamese names)
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Áo', 'ao', 'Các loại áo thun, sơ mi dệt lanh dệt mộc.', 'Áo', 'Bộ sưu tập áo thời trang tối giản' FROM categories WHERE slug = 'ao' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Quần', 'quan', 'Quần tây ly, quần linen ống rộng.', 'Quần', 'Các mẫu quần tối giản thanh lịch' FROM categories WHERE slug = 'quan' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Váy', 'vay', 'Váy lụa, chân váy xếp ly dáng rủ.', 'Váy', 'Chân váy lụa cao cấp' FROM categories WHERE slug = 'vay' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Đầm', 'dam', 'Đầm linen dáng suông nhẹ nhàng đô thị.', 'Đầm', 'Đầm lanh cao cấp' FROM categories WHERE slug = 'dam' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Áo khoác', 'ao-khoac', 'Áo blazer, áo khoác len merino.', 'Áo khoác', 'Áo khoác cao cấp' FROM categories WHERE slug = 'ao-khoac' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Giày', 'giay', 'Giày lười da, giày thể thao phong cách.', 'Giày', 'Giày da và thể thao' FROM categories WHERE slug = 'giay' ON CONFLICT DO NOTHING;
INSERT INTO category_translations (category_id, locale_code, name, slug, description, seo_title, seo_description)
SELECT id, 'vi', 'Phụ kiện', 'phu-kien', 'Túi tote da thật, thắt lưng da, mũ.', 'Phụ kiện', 'Phụ kiện thời trang tối giản' FROM categories WHERE slug = 'phu-kien' ON CONFLICT DO NOTHING;

-- 3. Deterministic product catalog
-- The previous catalog generated 100 products with RANDOM() colors/sizes and
-- category-level images. It made reruns unstable and could not map a color to
-- its actual image. Keep two focused fixtures here: trousers use apparel sizes
-- only, while sneakers use numeric sizes only.
INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'Tailored Black Trousers',
       'tailored-black-trousers',
       'Straight-leg tailored wool trousers with clean front pleats.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 'quan'
  AND b.slug = 'velawear'
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO products (name, slug, description, category_id, brand_id, status)
SELECT 'Minimal White Leather Sneakers',
       'minimal-white-leather-sneakers',
       'Minimal low-top sneakers made from smooth white leather.',
       c.id,
       b.id,
       'ACTIVE'
FROM categories c
CROSS JOIN brands b
WHERE c.slug = 'giay'
  AND b.slug = 'velawear'
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status;

INSERT INTO product_translations (
    product_id, locale_code, name, slug, short_description, description,
    material, care_instruction, seo_title, seo_description
)
SELECT p.id, 'vi', 'Quần tây đen may đo', p.slug,
       'Quần tây đen ống đứng với đường ly sắc nét.',
       'Thiết kế quần tây len pha, phom ống đứng và cạp may đo gọn gàng.',
       'Len pha', 'Giặt khô để giữ phom.',
       'Quần tây đen may đo', 'Quần tây đen dùng hệ size chữ S đến XL.'
FROM products p
WHERE p.slug = 'tailored-black-trousers'
ON CONFLICT (product_id, locale_code) DO NOTHING;

INSERT INTO product_translations (
    product_id, locale_code, name, slug, short_description, description,
    material, care_instruction, seo_title, seo_description
)
SELECT p.id, 'vi', 'Giày sneaker da trắng tối giản', p.slug,
       'Giày sneaker da trắng cổ thấp, dễ phối đồ.',
       'Thiết kế sneaker tối giản với thân da mềm và đế cao su bền.',
       'Da / Cao su', 'Lau sạch bằng khăn mềm.',
       'Giày sneaker da trắng tối giản', 'Giày sneaker chỉ dùng hệ size số 39 đến 42.'
FROM products p
WHERE p.slug = 'minimal-white-leather-sneakers'
ON CONFLICT (product_id, locale_code) DO NOTHING;

INSERT INTO product_variants (product_id, sku, price, stock_quantity, color_id, size_id, status)
SELECT p.id,
       'VW-TRS-BLK-' || s.name,
       899000.00,
       25,
       c.id,
       s.id,
       'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'tailored-black-trousers'
  AND c.name = 'Black'
  AND s.name IN ('S', 'M', 'L', 'XL')
ON CONFLICT (product_id, color_id, size_id) DO UPDATE
SET sku = EXCLUDED.sku,
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    status = EXCLUDED.status,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO product_variants (product_id, sku, price, stock_quantity, color_id, size_id, status)
SELECT p.id,
       'VW-SNK-WHT-' || s.name,
       1299000.00,
       20,
       c.id,
       s.id,
       'ACTIVE'
FROM products p
CROSS JOIN colors c
CROSS JOIN sizes s
WHERE p.slug = 'minimal-white-leather-sneakers'
  AND c.name = 'White'
  AND s.name IN ('39', '40', '41', '42')
ON CONFLICT (product_id, color_id, size_id) DO UPDATE
SET sku = EXCLUDED.sku,
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    status = EXCLUDED.status,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM product_images WHERE product_id IN (
    SELECT id FROM products WHERE slug IN ('tailored-black-trousers', 'minimal-white-leather-sneakers')
);

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/tailored_black_trousers.png', TRUE, 1
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-TRS-BLK-M'
WHERE p.slug = 'tailored-black-trousers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/tailored_black_trousers_back.png', FALSE, 2
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-TRS-BLK-M'
WHERE p.slug = 'tailored-black-trousers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/tailored_black_trousers_detail.png', FALSE, 3
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-TRS-BLK-M'
WHERE p.slug = 'tailored-black-trousers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/sneaker_af_1.png', TRUE, 1
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-SNK-WHT-39'
WHERE p.slug = 'minimal-white-leather-sneakers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/sneaker_af_2.png', FALSE, 2
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-SNK-WHT-39'
WHERE p.slug = 'minimal-white-leather-sneakers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/sneaker_af_3.png', FALSE, 3
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-SNK-WHT-39'
WHERE p.slug = 'minimal-white-leather-sneakers';

INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
SELECT p.id, pv.id, '/uploads/products/sneaker_af_4.png', FALSE, 4
FROM products p
JOIN product_variants pv ON pv.product_id = p.id AND pv.sku = 'VW-SNK-WHT-39'
WHERE p.slug = 'minimal-white-leather-sneakers';

-- The former RANDOM()-based 100-product generator was removed. Product data
-- now comes exclusively from R__3_dev_catalog_products.sql.

-- The deterministic catalog is owned by R__3_dev_catalog_products.sql.

-- Product scope shared by the deterministic feature fixtures below. Resolve it
-- from ownership metadata so the catalog can grow without repeating 100 slugs.
DROP TABLE IF EXISTS dev_managed_product_scope;
CREATE TEMP TABLE dev_managed_product_scope (
    slug VARCHAR(280) PRIMARY KEY
);

INSERT INTO dev_managed_product_scope (slug)
SELECT product.slug
FROM products product
JOIN product_attributes owner
  ON owner.product_id = product.id
 AND owner.name = 'SeedOwner'
 AND owner.value = 'R3_PRODUCT_CATALOG_100'
WHERE product.status = 'ACTIVE'
  AND product.deleted_at IS NULL;

-- 8. User Addresses (deterministic fixture addresses)
-- These exact patterns came only from the disabled random seed. Removing them
-- does not touch normal addresses, even when they belong to a fixture account.
DELETE FROM user_addresses address
WHERE address.is_default = FALSE
  AND address.receiver_name ~ '^Receiver [0-9]+$'
  AND address.phone ~ '^09[0-9]{8}$'
  AND address.province ~ '^Province [0-9]+$'
  AND address.ward ~ '^Ward [0-9]+$'
  AND address.address_detail ~ '^Address Detail [0-9]+$';

WITH address_fixture (
    email,
    receiver_name,
    phone,
    province,
    ward,
    address_detail
) AS (
    VALUES
        ('user@velawear.local', 'Công Anh', '0930000001', 'Ho Chi Minh', 'Thao Dien', '21 Xuan Thuy'),
        ('linh@velawear.local', 'Linh Nguyen', '0930000002', 'Ha Noi', 'Dich Vong', '18 Cau Giay'),
        ('minh@velawear.local', 'Minh Tran', '0930000003', 'Da Nang', 'Hai Chau', '86 Tran Phu')
)
INSERT INTO user_addresses (
    user_id,
    receiver_name,
    phone,
    province,
    ward,
    address_detail,
    is_default
)
SELECT
    customer.id,
    fixture.receiver_name,
    fixture.phone,
    fixture.province,
    fixture.ward,
    fixture.address_detail,
    FALSE
FROM address_fixture fixture
JOIN users customer ON customer.email = fixture.email
WHERE NOT EXISTS (
    SELECT 1
    FROM user_addresses existing
    WHERE existing.user_id = customer.id
      AND existing.phone = fixture.phone
      AND existing.address_detail = fixture.address_detail
);

-- 9. Carts (Ensure fixture customers have a cart)
INSERT INTO carts (user_id)
SELECT customer.id
FROM users customer
WHERE customer.email IN (
    'user@velawear.local',
    'linh@velawear.local',
    'minh@velawear.local'
)
ON CONFLICT (user_id) DO NOTHING;

-- 10. Cart Items (three stable managed variants per fixture customer)
WITH fixture_customers (email, variant_offset) AS (
    VALUES
        ('user@velawear.local', 0),
        ('linh@velawear.local', 7),
        ('minh@velawear.local', 14)
), managed_variants AS (
    SELECT
        variant.id,
        ROW_NUMBER() OVER (ORDER BY variant.sku) - 1 AS fixture_index,
        COUNT(*) OVER () AS fixture_count
    FROM product_variants variant
    JOIN products product ON product.id = variant.product_id
    JOIN dev_managed_product_scope managed ON managed.slug = product.slug
    WHERE variant.deleted_at IS NULL
      AND variant.status = 'ACTIVE'
), selected_items AS (
    SELECT
        cart.id AS cart_id,
        variant.id AS variant_id,
        slot.slot_number + 1 AS quantity
    FROM fixture_customers fixture
    JOIN users customer ON customer.email = fixture.email
    JOIN carts cart ON cart.user_id = customer.id
    CROSS JOIN generate_series(0, 2) AS slot(slot_number)
    JOIN managed_variants variant
      ON variant.fixture_index = MOD(
          (fixture.variant_offset + slot.slot_number)::BIGINT,
          variant.fixture_count
      )
)
INSERT INTO cart_items (cart_id, variant_id, quantity)
SELECT selected.cart_id, selected.variant_id, selected.quantity
FROM selected_items selected
ON CONFLICT (cart_id, variant_id) DO UPDATE
SET quantity = EXCLUDED.quantity;

-- 11. Coupons (100 stable pagination fixtures)
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
SELECT
    'COUPON' || fixture.fixture_number,
    'PERCENTAGE',
    10.00,
    100000.00,
    50000.00,
    100,
    0,
    TIMESTAMPTZ '2025-01-01 00:00:00+07',
    TIMESTAMPTZ '2099-12-31 23:59:59+07',
    'ACTIVE'
FROM generate_series(1, 100) AS fixture(fixture_number)
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

-- 12. Orders (100 records)
-- This section is deliberately self-contained. DevSeedDataIntegrationTest
-- extracts markers 12..16 and executes them again to verify idempotency.
DROP TABLE IF EXISTS dev_managed_product_scope;
CREATE TEMP TABLE dev_managed_product_scope (
    slug VARCHAR(280) PRIMARY KEY
);

INSERT INTO dev_managed_product_scope (slug)
SELECT product.slug
FROM products product
JOIN product_attributes owner
  ON owner.product_id = product.id
 AND owner.name = 'SeedOwner'
 AND owner.value = 'R3_PRODUCT_CATALOG_100'
WHERE product.status = 'ACTIVE'
  AND product.deleted_at IS NULL;

WITH mock_customer AS (
    SELECT id
    FROM users
    WHERE email = 'user@velawear.local'
), mock_order_fixture AS (
    SELECT generate_series(1, 100) AS fixture_number
)
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
    mock_customer.id,
    'VW-MOCK-' || (1000 + fixture.fixture_number),
    'COMPLETED',
    0.00,
    30000.00,
    0.00,
    30000.00,
    'Mock Customer ' || fixture.fixture_number,
    '09' || LPAD(fixture.fixture_number::TEXT, 8, '0'),
    'Mock Address ' || fixture.fixture_number,
    'COD',
    'PAID',
    TIMESTAMPTZ '2025-02-01 08:00:00+07'
        + ((fixture.fixture_number - 1) * INTERVAL '1 hour'),
    TIMESTAMPTZ '2025-02-01 09:00:00+07'
        + ((fixture.fixture_number - 1) * INTERVAL '1 hour')
FROM mock_order_fixture fixture
CROSS JOIN mock_customer
ON CONFLICT (order_code) DO UPDATE
SET
    user_id = EXCLUDED.user_id,
    status = EXCLUDED.status,
    shipping_fee = EXCLUDED.shipping_fee,
    receiver_name = EXCLUDED.receiver_name,
    receiver_phone = EXCLUDED.receiver_phone,
    receiver_address = EXCLUDED.receiver_address,
    payment_method = EXCLUDED.payment_method,
    payment_status = EXCLUDED.payment_status,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

-- 13. Order Items (exactly one deterministic BASE item per VW-MOCK order)
DROP TABLE IF EXISTS dev_mock_order_item_fixture;
CREATE TEMP TABLE dev_mock_order_item_fixture AS
WITH mock_orders AS (
    SELECT
        customer_order.id,
        customer_order.order_code,
        ROW_NUMBER() OVER (
            ORDER BY SUBSTRING(customer_order.order_code FROM '([0-9]+)$')::INTEGER
        ) - 1 AS fixture_index
    FROM orders customer_order
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
), available_variants AS (
    SELECT
        variant.id,
        variant.sku,
        variant.price,
        product.name AS product_name,
        product.slug AS product_slug,
        NULLIF(CONCAT_WS(' / ', color.name, size.name), '') AS variant_name,
        variant_image.image,
        ROW_NUMBER() OVER (ORDER BY variant.sku) - 1 AS fixture_index,
        COUNT(*) OVER () AS fixture_count
    FROM product_variants variant
    JOIN products product ON product.id = variant.product_id
    JOIN dev_managed_product_scope managed ON managed.slug = product.slug
    LEFT JOIN colors color ON color.id = variant.color_id
    LEFT JOIN sizes size ON size.id = variant.size_id
    JOIN LATERAL (
        SELECT product_image.image
        FROM product_images product_image
        LEFT JOIN product_variants image_variant
          ON image_variant.id = product_image.variant_id
        WHERE product_image.product_id = product.id
          AND (
              product_image.variant_id = variant.id
              OR image_variant.color_id IS NOT DISTINCT FROM variant.color_id
              OR product_image.variant_id IS NULL
          )
        ORDER BY
            CASE WHEN product_image.variant_id = variant.id THEN 0 ELSE 1 END,
            CASE WHEN product_image.is_thumbnail THEN 0 ELSE 1 END,
            product_image.sort_order,
            product_image.id
        LIMIT 1
    ) variant_image ON TRUE
    WHERE variant.deleted_at IS NULL
      AND variant.status = 'ACTIVE'
      AND product.deleted_at IS NULL
      AND product.status = 'ACTIVE'
), selected_items AS (
    SELECT
        mock_order.id AS order_id,
        variant.id AS variant_id,
        variant.product_name,
        variant.product_slug,
        variant.variant_name,
        variant.sku,
        variant.image,
        variant.price
    FROM mock_orders mock_order
    JOIN available_variants variant
      ON variant.fixture_index = MOD(mock_order.fixture_index, variant.fixture_count)
)
SELECT
    selected.order_id,
    selected.variant_id,
    selected.product_name,
    selected.product_slug,
    selected.variant_name,
    selected.sku,
    selected.image,
    selected.price AS list_price,
    selected.price,
    1 AS quantity,
    selected.price AS subtotal,
    'CONFIRMED'::VARCHAR AS status,
    'BASE'::VARCHAR AS price_source
FROM selected_items selected;

-- Preserve the existing order-item ID so seeded reviews and audit references
-- stay attached when the managed catalog changes.
WITH primary_item AS (
    SELECT DISTINCT ON (existing.order_id)
        existing.id,
        existing.order_id
    FROM order_items existing
    JOIN dev_mock_order_item_fixture fixture
      ON fixture.order_id = existing.order_id
    ORDER BY existing.order_id, existing.id
)
UPDATE order_items existing
SET
    variant_id = fixture.variant_id,
    product_name = fixture.product_name,
    product_slug = fixture.product_slug,
    variant_name = fixture.variant_name,
    sku = fixture.sku,
    image = fixture.image,
    list_price = fixture.list_price,
    price = fixture.price,
    quantity = fixture.quantity,
    subtotal = fixture.subtotal,
    status = fixture.status,
    price_source = fixture.price_source,
    sale_campaign_item_id = NULL,
    sale_campaign_code = NULL,
    sale_campaign_name = NULL
FROM primary_item primary_fixture
JOIN dev_mock_order_item_fixture fixture
  ON fixture.order_id = primary_fixture.order_id
WHERE existing.id = primary_fixture.id;

INSERT INTO order_items (
    order_id,
    variant_id,
    product_name,
    product_slug,
    variant_name,
    sku,
    image,
    list_price,
    price,
    quantity,
    subtotal,
    status,
    price_source
)
SELECT
    fixture.order_id,
    fixture.variant_id,
    fixture.product_name,
    fixture.product_slug,
    fixture.variant_name,
    fixture.sku,
    fixture.image,
    fixture.list_price,
    fixture.price,
    fixture.quantity,
    fixture.subtotal,
    fixture.status,
    fixture.price_source
FROM dev_mock_order_item_fixture fixture
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items existing
    WHERE existing.order_id = fixture.order_id
);

-- Order header totals are always derived from their item snapshots.
WITH mock_totals AS (
    SELECT item.order_id, SUM(item.subtotal) AS subtotal
    FROM order_items item
    JOIN orders customer_order ON customer_order.id = item.order_id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
    GROUP BY item.order_id
)
UPDATE orders customer_order
SET
    subtotal = mock_totals.subtotal,
    final_amount = mock_totals.subtotal
        + customer_order.shipping_fee
        - customer_order.discount_amount
FROM mock_totals
WHERE customer_order.id = mock_totals.order_id;

-- 14. Payments (one stable payment per VW-MOCK order)
UPDATE payments payment
SET
    provider = 'COD',
    amount = customer_order.final_amount,
    status = 'SUCCESS',
    paid_at = customer_order.updated_at
FROM orders customer_order
WHERE payment.order_id = customer_order.id
  AND customer_order.order_code ~ '^VW-MOCK-[0-9]+$';

INSERT INTO payments (order_id, provider, transaction_code, amount, status, paid_at)
SELECT
    customer_order.id,
    'COD',
    'MOCK-COD-' || customer_order.order_code,
    customer_order.final_amount,
    'SUCCESS',
    customer_order.updated_at
FROM orders customer_order
WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
  AND NOT EXISTS (
      SELECT 1
      FROM payments existing
      WHERE existing.order_id = customer_order.id
  )
ON CONFLICT (transaction_code) DO UPDATE
SET
    order_id = EXCLUDED.order_id,
    provider = EXCLUDED.provider,
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    paid_at = EXCLUDED.paid_at;

-- 15. Payment Transactions (one stable gateway snapshot per mock payment)
WITH mock_payments AS (
    SELECT DISTINCT ON (customer_order.id)
        customer_order.id AS order_id,
        customer_order.order_code,
        payment.id AS payment_id
    FROM orders customer_order
    JOIN payments payment ON payment.order_id = customer_order.id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
    ORDER BY customer_order.id, payment.id
)
INSERT INTO payment_transactions (payment_id, transaction_code, status, gateway_response)
SELECT
    mock_payment.payment_id,
    'MOCK-GATEWAY-' || mock_payment.order_code,
    'SUCCESS',
    '{"status":"success","fixture":"VW-MOCK"}'::JSONB
FROM mock_payments mock_payment
ON CONFLICT (transaction_code) WHERE transaction_code IS NOT NULL DO UPDATE
SET
    payment_id = EXCLUDED.payment_id,
    status = EXCLUDED.status,
    gateway_response = EXCLUDED.gateway_response;

DROP TABLE IF EXISTS dev_mock_order_item_fixture;
DROP TABLE IF EXISTS dev_managed_product_scope;

-- 16. Coupon Usages (100 records)
-- Recreate the scope because marker 12..16 is also executed as a standalone
-- idempotency test and must clean up every temporary object it creates.
CREATE TEMP TABLE dev_managed_product_scope (
    slug VARCHAR(280) PRIMARY KEY
);

INSERT INTO dev_managed_product_scope (slug)
SELECT product.slug
FROM products product
JOIN product_attributes owner
  ON owner.product_id = product.id
 AND owner.name = 'SeedOwner'
 AND owner.value = 'R3_PRODUCT_CATALOG_100'
WHERE product.status = 'ACTIVE'
  AND product.deleted_at IS NULL;

-- Remove the occasional non-mock usage produced by the old random loop only
-- when its coupon code and fixed amount make its seed ownership unambiguous.
DELETE FROM coupon_usages usage
USING coupons coupon, orders customer_order
WHERE usage.coupon_id = coupon.id
  AND usage.order_id = customer_order.id
  AND coupon.code ~ '^COUPON([1-9]|[1-9][0-9]|100)$'
  AND usage.discount_amount = 10000.00
  AND customer_order.order_code !~ '^VW-MOCK-[0-9]+$';

WITH mock_usage_fixture AS (
    SELECT
        customer_order.id AS order_id,
        customer_order.user_id,
        customer_order.updated_at AS used_at,
        coupon.id AS coupon_id,
        LEAST(
            coupon.max_discount,
            ROUND(customer_order.subtotal * coupon.value / 100.00, 2)
        ) AS discount_amount
    FROM orders customer_order
    JOIN coupons coupon
      ON coupon.code = 'COUPON'
          || (SUBSTRING(customer_order.order_code FROM '([0-9]+)$')::INTEGER - 1000)
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
)
INSERT INTO coupon_usages (
    coupon_id,
    user_id,
    order_id,
    discount_amount,
    used_at
)
SELECT
    fixture.coupon_id,
    fixture.user_id,
    fixture.order_id,
    fixture.discount_amount,
    fixture.used_at
FROM mock_usage_fixture fixture
ON CONFLICT (order_id) DO UPDATE
SET
    coupon_id = EXCLUDED.coupon_id,
    user_id = EXCLUDED.user_id,
    discount_amount = EXCLUDED.discount_amount,
    used_at = EXCLUDED.used_at;

UPDATE orders customer_order
SET
    discount_amount = usage.discount_amount,
    final_amount = customer_order.subtotal
        + customer_order.shipping_fee
        - usage.discount_amount
FROM coupon_usages usage
WHERE usage.order_id = customer_order.id
  AND customer_order.order_code ~ '^VW-MOCK-[0-9]+$';

UPDATE payments payment
SET amount = customer_order.final_amount
FROM orders customer_order
WHERE payment.order_id = customer_order.id
  AND customer_order.order_code ~ '^VW-MOCK-[0-9]+$';

UPDATE coupons coupon
SET used_count = (
    SELECT COUNT(*)::INTEGER
    FROM coupon_usages usage
    WHERE usage.coupon_id = coupon.id
)
WHERE coupon.code ~ '^COUPON([1-9]|[1-9][0-9]|100)$';

-- 17. Reviews (100 records)
-- A random review could previously land on a non-mock order. Its generated
-- comment identifies it without relying on an unstable database ID.
DELETE FROM reviews review
USING order_items order_item, orders customer_order
WHERE review.order_item_id = order_item.id
  AND order_item.order_id = customer_order.id
  AND review.comment ~ '^Review for product: [0-9]+$'
  AND customer_order.order_code !~ '^VW-MOCK-[0-9]+$';

-- Retain the oldest review ID per managed mock item, then reconcile its owner
-- with the order owner. Review images on removed duplicates cascade safely.
WITH ranked_reviews AS (
    SELECT
        review.id,
        ROW_NUMBER() OVER (
            PARTITION BY review.order_item_id
            ORDER BY review.id
        ) AS fixture_rank
    FROM reviews review
    JOIN order_items order_item ON order_item.id = review.order_item_id
    JOIN orders customer_order ON customer_order.id = order_item.order_id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
)
DELETE FROM reviews duplicate
USING ranked_reviews ranked
WHERE duplicate.id = ranked.id
  AND ranked.fixture_rank > 1;

WITH review_fixture AS (
    SELECT
        customer_order.user_id,
        order_item.id AS order_item_id,
        (
            (SUBSTRING(customer_order.order_code FROM '([0-9]+)$')::INTEGER - 1001) % 5
        ) + 1 AS rating,
        'Deterministic mock review for ' || customer_order.order_code AS comment
    FROM orders customer_order
    JOIN order_items order_item ON order_item.order_id = customer_order.id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
)
UPDATE reviews existing
SET
    user_id = fixture.user_id,
    rating = fixture.rating,
    comment = fixture.comment
FROM review_fixture fixture
WHERE existing.order_item_id = fixture.order_item_id;

WITH review_fixture AS (
    SELECT
        customer_order.user_id,
        order_item.id AS order_item_id,
        (
            (SUBSTRING(customer_order.order_code FROM '([0-9]+)$')::INTEGER - 1001) % 5
        ) + 1 AS rating,
        'Deterministic mock review for ' || customer_order.order_code AS comment
    FROM orders customer_order
    JOIN order_items order_item ON order_item.order_id = customer_order.id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
)
INSERT INTO reviews (user_id, order_item_id, rating, comment)
SELECT
    fixture.user_id,
    fixture.order_item_id,
    fixture.rating,
    fixture.comment
FROM review_fixture fixture
WHERE NOT EXISTS (
    SELECT 1
    FROM reviews existing
    WHERE existing.order_item_id = fixture.order_item_id
)
ON CONFLICT (user_id, order_item_id) DO UPDATE
SET
    rating = EXCLUDED.rating,
    comment = EXCLUDED.comment;

-- 18. Review Images (100 records)
WITH ranked_images AS (
    SELECT
        review_image.id,
        ROW_NUMBER() OVER (
            PARTITION BY review_image.review_id
            ORDER BY review_image.id
        ) AS fixture_rank
    FROM review_images review_image
    JOIN reviews review ON review.id = review_image.review_id
    JOIN order_items order_item ON order_item.id = review.order_item_id
    JOIN orders customer_order ON customer_order.id = order_item.order_id
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
)
DELETE FROM review_images duplicate
USING ranked_images ranked
WHERE duplicate.id = ranked.id
  AND ranked.fixture_rank > 1;

UPDATE review_images existing
SET image = order_item.image
FROM reviews review
JOIN order_items order_item ON order_item.id = review.order_item_id
JOIN orders customer_order ON customer_order.id = order_item.order_id
WHERE existing.review_id = review.id
  AND customer_order.order_code ~ '^VW-MOCK-[0-9]+$';

INSERT INTO review_images (review_id, image)
SELECT review.id, order_item.image
FROM reviews review
JOIN order_items order_item ON order_item.id = review.order_item_id
JOIN orders customer_order ON customer_order.id = order_item.order_id
WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
  AND NOT EXISTS (
      SELECT 1
      FROM review_images existing
      WHERE existing.review_id = review.id
  );

-- 19. Inventory Logs (100 records)
DELETE FROM inventory_logs inventory_log
WHERE inventory_log.type = 'IMPORT'
  AND inventory_log.reason ~ '^Mock inventory import [0-9]+$';

WITH ranked_logs AS (
    SELECT
        inventory_log.id,
        ROW_NUMBER() OVER (
            PARTITION BY inventory_log.variant_id, inventory_log.reason
            ORDER BY inventory_log.id
        ) AS fixture_rank
    FROM inventory_logs inventory_log
    JOIN product_variants variant ON variant.id = inventory_log.variant_id
    JOIN products product ON product.id = variant.product_id
    JOIN dev_managed_product_scope managed ON managed.slug = product.slug
    WHERE inventory_log.type = 'IMPORT'
      AND inventory_log.reason = 'Deterministic dev stock import: ' || variant.sku
)
DELETE FROM inventory_logs duplicate
USING ranked_logs ranked
WHERE duplicate.id = ranked.id
  AND ranked.fixture_rank > 1;

UPDATE inventory_logs inventory_log
SET change_quantity = GREATEST(variant.stock_quantity, 1)
FROM product_variants variant
JOIN products product ON product.id = variant.product_id
JOIN dev_managed_product_scope managed ON managed.slug = product.slug
WHERE inventory_log.variant_id = variant.id
  AND inventory_log.type = 'IMPORT'
  AND inventory_log.reason = 'Deterministic dev stock import: ' || variant.sku;

INSERT INTO inventory_logs (variant_id, change_quantity, type, reason)
SELECT
    variant.id,
    GREATEST(variant.stock_quantity, 1),
    'IMPORT',
    'Deterministic dev stock import: ' || variant.sku
FROM product_variants variant
JOIN products product ON product.id = variant.product_id
JOIN dev_managed_product_scope managed ON managed.slug = product.slug
WHERE variant.deleted_at IS NULL
  AND variant.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM inventory_logs existing
      WHERE existing.variant_id = variant.id
        AND existing.type = 'IMPORT'
        AND existing.reason = 'Deterministic dev stock import: ' || variant.sku
  );

-- 20. Order Status Histories (100 records)
DELETE FROM order_status_histories history
WHERE history.reason ~ '^Status change [0-9]+$';

WITH history_fixture (
    order_id,
    from_status,
    to_status,
    reason,
    created_at
) AS (
    SELECT
        customer_order.id,
        NULL::VARCHAR,
        'PENDING',
        'VW-MOCK fixture created',
        customer_order.created_at
    FROM orders customer_order
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'

    UNION ALL

    SELECT
        customer_order.id,
        'PENDING',
        'COMPLETED',
        'VW-MOCK fixture completed',
        customer_order.updated_at
    FROM orders customer_order
    WHERE customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
), actor AS (
    SELECT id
    FROM users
    WHERE email = 'staff@velawear.local'
)
INSERT INTO order_status_histories (
    order_id,
    from_status,
    to_status,
    changed_by,
    reason,
    created_at
)
SELECT
    fixture.order_id,
    fixture.from_status,
    fixture.to_status,
    actor.id,
    fixture.reason,
    fixture.created_at
FROM history_fixture fixture
CROSS JOIN actor
WHERE NOT EXISTS (
    SELECT 1
    FROM order_status_histories existing
    WHERE existing.order_id = fixture.order_id
      AND existing.reason = fixture.reason
);

-- 21. Wishlists (100 records)
WITH fixture_customers (email, product_offset) AS (
    VALUES
        ('user@velawear.local', 0),
        ('linh@velawear.local', 8),
        ('minh@velawear.local', 16)
), managed_products AS (
    SELECT
        product.id,
        ROW_NUMBER() OVER (ORDER BY product.slug) - 1 AS fixture_index,
        COUNT(*) OVER () AS fixture_count
    FROM products product
    JOIN dev_managed_product_scope managed ON managed.slug = product.slug
    WHERE product.deleted_at IS NULL
      AND product.status = 'ACTIVE'
), selected_wishlists AS (
    SELECT customer.id AS user_id, product.id AS product_id
    FROM fixture_customers fixture
    JOIN users customer ON customer.email = fixture.email
    CROSS JOIN generate_series(0, 3) AS slot(slot_number)
    JOIN managed_products product
      ON product.fixture_index = MOD(
          (fixture.product_offset + slot.slot_number)::BIGINT,
          product.fixture_count
      )
)
INSERT INTO wishlists (user_id, product_id)
SELECT fixture.user_id, fixture.product_id
FROM selected_wishlists fixture
ON CONFLICT (user_id, product_id) DO NOTHING;

DROP TABLE IF EXISTS dev_managed_product_scope;
