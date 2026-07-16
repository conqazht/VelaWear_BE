-- Deterministic bilingual catalog fixtures.
--
-- Versioned migrations own the locale/translation schema. This repeatable
-- migration only reconciles development content after R__1..R__3 have seeded
-- the stable products and categories.

-- Clear a non-Vietnamese default first. Updating vi=true before clearing an
-- existing default would violate uidx_locales_single_default mid-statement.
UPDATE locales
SET is_default = FALSE
WHERE code <> 'vi'
  AND is_default = TRUE;

INSERT INTO locales (code, name, is_default, is_enabled)
VALUES
    ('vi', 'Vietnamese', TRUE, TRUE),
    ('en', 'English', FALSE, TRUE)
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    is_default = EXCLUDED.is_default,
    is_enabled = EXCLUDED.is_enabled;

-- Older dev seeds assigned these Vietnamese slugs to the English-core
-- categories. Move only those known legacy rows first so the bulk upsert below
-- can assign the canonical slugs without a transient unique-key collision.
UPDATE category_translations AS translation
SET slug = legacy.replacement_slug
FROM (
    VALUES
        ('dresses', 'vi', 'dam', 'dam-nu'),
        ('jackets', 'vi', 'ao-khoac', 'ao-khoac-nam'),
        ('accessories', 'vi', 'phu-kien', 'phu-kien-co-ban')
) AS legacy(base_slug, locale_code, legacy_slug, replacement_slug)
JOIN categories AS category
  ON category.slug = legacy.base_slug
WHERE translation.category_id = category.id
  AND translation.locale_code = legacy.locale_code
  AND translation.slug = legacy.legacy_slug;

WITH category_fixture (
    base_slug,
    locale_code,
    name,
    localized_slug,
    description,
    seo_title,
    seo_description
) AS (
    VALUES
        ('men', 'vi', 'Nam', 'nam',
         'Trang phục nam tối giản, dễ phối cho nhịp sống hằng ngày.',
         'Thời trang nam VelaWear',
         'Khám phá trang phục nam tối giản, hiện đại và dễ ứng dụng từ VelaWear.'),
        ('men', 'en', 'Men', 'men',
         'Minimal menswear designed for effortless everyday styling.',
         'VelaWear Menswear',
         'Explore modern, versatile menswear designed by VelaWear.'),

        ('women', 'vi', 'Nữ', 'nu',
         'Trang phục nữ thanh lịch với phom dáng hiện đại và chất liệu thoải mái.',
         'Thời trang nữ VelaWear',
         'Khám phá thời trang nữ thanh lịch, hiện đại và thoải mái từ VelaWear.'),
        ('women', 'en', 'Women', 'women',
         'Elegant womenswear with modern silhouettes and comfortable fabrics.',
         'VelaWear Womenswear',
         'Explore elegant, modern and comfortable womenswear from VelaWear.'),

        ('t-shirts', 'vi', 'Áo thun nam', 'ao-thun-nam',
         'Áo thun nam mềm mại, thoáng mát và phù hợp để mặc mỗi ngày.',
         'Áo thun nam',
         'Các mẫu áo thun nam tối giản với chất liệu thoải mái.'),
        ('t-shirts', 'en', 'Men''s T-Shirts', 'mens-t-shirts',
         'Soft and breathable men''s T-shirts for everyday wear.',
         'Men''s T-Shirts',
         'Shop minimal men''s T-shirts made from comfortable fabrics.'),

        ('dresses', 'vi', 'Đầm nữ', 'dam-nu',
         'Đầm nữ nhẹ nhàng, thanh lịch dành cho nhiều hoàn cảnh.',
         'Đầm nữ thanh lịch',
         'Bộ sưu tập đầm nữ thanh lịch với phom dáng hiện đại.'),
        ('dresses', 'en', 'Women''s Dresses', 'womens-dresses',
         'Light and elegant women''s dresses for versatile occasions.',
         'Women''s Dresses',
         'Discover elegant women''s dresses with contemporary silhouettes.'),

        ('accessories', 'vi', 'Phụ kiện cơ bản', 'phu-kien-co-ban',
         'Những phụ kiện thiết yếu giúp hoàn thiện trang phục hằng ngày.',
         'Phụ kiện cơ bản',
         'Phụ kiện thiết yếu, tối giản dành cho phong cách hằng ngày.'),
        ('accessories', 'en', 'Everyday Accessories', 'everyday-accessories',
         'Essential accessories that complete an everyday wardrobe.',
         'Everyday Accessories',
         'Discover minimal accessories for effortless everyday styling.'),

        ('outerwear', 'vi', 'Trang phục khoác', 'trang-phuc-khoac',
         'Trang phục khoác có cấu trúc, phù hợp với thời tiết chuyển mùa.',
         'Trang phục khoác',
         'Khám phá áo khoác ngoài hiện đại với chất liệu cao cấp.'),
        ('outerwear', 'en', 'Outerwear', 'outerwear',
         'Structured outer layers designed for transitional weather.',
         'Modern Outerwear',
         'Explore modern outerwear made with premium materials.'),

        ('jackets', 'vi', 'Áo khoác nam', 'ao-khoac-nam',
         'Áo khoác nam hiện đại với đường cắt gọn gàng.',
         'Áo khoác nam',
         'Các mẫu áo khoác nam hiện đại, dễ kết hợp.'),
        ('jackets', 'en', 'Men''s Jackets', 'mens-jackets',
         'Modern men''s jackets with clean, structured tailoring.',
         'Men''s Jackets',
         'Shop versatile men''s jackets with clean contemporary cuts.'),

        ('ao', 'vi', 'Áo', 'ao',
         'Các loại áo thun và sơ mi với chất liệu tự nhiên, thoáng mát.',
         'Áo thời trang tối giản',
         'Bộ sưu tập áo thun và sơ mi tối giản, dễ phối đồ.'),
        ('ao', 'en', 'Shirts & Tops', 'shirts-and-tops',
         'T-shirts, shirts and tops made from breathable natural fabrics.',
         'Minimal Shirts & Tops',
         'Explore versatile T-shirts, shirts and tops for a minimal wardrobe.'),

        ('quan', 'vi', 'Quần', 'quan',
         'Quần tây may đo và quần linen với phom dáng thanh lịch.',
         'Quần tối giản',
         'Các mẫu quần tây và quần linen thanh lịch, dễ ứng dụng.'),
        ('quan', 'en', 'Pants & Trousers', 'pants-and-trousers',
         'Tailored trousers and relaxed linen pants with refined silhouettes.',
         'Minimal Pants & Trousers',
         'Discover tailored trousers and relaxed pants for everyday elegance.'),

        ('vay', 'vi', 'Váy', 'vay',
         'Chân váy lụa và váy xếp ly có độ rủ tự nhiên.',
         'Váy thanh lịch',
         'Chân váy lụa và váy xếp ly cao cấp với phom dáng mềm mại.'),
        ('vay', 'en', 'Skirts', 'skirts',
         'Silk and pleated skirts with naturally fluid movement.',
         'Elegant Skirts',
         'Explore premium silk and pleated skirts with graceful silhouettes.'),

        ('dam', 'vi', 'Đầm', 'dam',
         'Đầm linen và đầm lụa dáng suông dành cho phong cách đô thị.',
         'Đầm tối giản cao cấp',
         'Đầm linen và đầm lụa cao cấp với thiết kế tối giản.'),
        ('dam', 'en', 'Dresses', 'dresses',
         'Relaxed linen and silk dresses designed for modern city life.',
         'Premium Minimal Dresses',
         'Discover premium linen and silk dresses with minimal silhouettes.'),

        ('ao-khoac', 'vi', 'Áo khoác', 'ao-khoac',
         'Blazer và áo khoác cao cấp với đường may tinh gọn.',
         'Áo khoác cao cấp',
         'Bộ sưu tập blazer và áo khoác cao cấp cho phong cách hiện đại.'),
        ('ao-khoac', 'en', 'Coats & Jackets', 'coats-and-jackets',
         'Premium blazers, coats and jackets with refined construction.',
         'Premium Coats & Jackets',
         'Explore refined blazers, coats and jackets for a modern wardrobe.'),

        ('giay', 'vi', 'Giày', 'giay',
         'Giày da và sneaker tối giản, thoải mái cho ngày dài.',
         'Giày da và sneaker',
         'Khám phá giày da và sneaker tối giản, bền đẹp.'),
        ('giay', 'en', 'Shoes', 'shoes',
         'Minimal leather shoes and sneakers built for all-day comfort.',
         'Leather Shoes & Sneakers',
         'Explore durable minimal leather shoes and everyday sneakers.'),

        ('phu-kien', 'vi', 'Phụ kiện', 'phu-kien',
         'Túi, thắt lưng và phụ kiện thời trang có thiết kế tối giản.',
         'Phụ kiện thời trang',
         'Phụ kiện thời trang tối giản giúp hoàn thiện mọi trang phục.'),
        ('phu-kien', 'en', 'Fashion Accessories', 'fashion-accessories',
         'Minimal bags, belts and accessories that complete every look.',
         'Minimal Fashion Accessories',
         'Shop refined bags, belts and accessories for a complete wardrobe.')
), category_entity AS (
    SELECT DISTINCT ON (fixture_key.base_slug)
        fixture_key.base_slug,
        category.id
    FROM (SELECT DISTINCT base_slug FROM category_fixture) fixture_key
    JOIN categories category
      ON category.slug = fixture_key.base_slug
      OR EXISTS (
          SELECT 1
          FROM category_translations existing_translation
          JOIN category_fixture fixture_alias
            ON fixture_alias.base_slug = fixture_key.base_slug
           AND fixture_alias.localized_slug = existing_translation.slug
          WHERE existing_translation.category_id = category.id
      )
    ORDER BY
        fixture_key.base_slug,
        CASE WHEN category.slug = fixture_key.base_slug THEN 0 ELSE 1 END,
        category.id
)
INSERT INTO category_translations (
    category_id,
    locale_code,
    name,
    slug,
    description,
    seo_title,
    seo_description
)
SELECT
    category.id,
    fixture.locale_code,
    fixture.name,
    fixture.localized_slug,
    fixture.description,
    fixture.seo_title,
    fixture.seo_description
FROM category_fixture fixture
JOIN category_entity category ON category.base_slug = fixture.base_slug
JOIN locales locale ON locale.code = fixture.locale_code
ON CONFLICT (category_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    description = EXCLUDED.description,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description;

WITH product_fixture (
    base_slug,
    locale_code,
    name,
    localized_slug,
    short_description,
    description,
    material,
    care_instruction,
    seo_title,
    seo_description
) AS (
    VALUES
        ('essential-cotton-tee', 'vi',
         'Áo thun cotton thiết yếu', 'ao-thun-cotton-thiet-yeu',
         'Áo thun cotton mềm mại với phom suông thoải mái.',
         'Mẫu áo thun mặc hằng ngày với cổ tròn gọn, vai rủ nhẹ và chất cotton thoáng mát.',
         '100% cotton', 'Giặt máy bằng nước lạnh, phơi trong bóng râm.',
         'Áo thun cotton thiết yếu',
         'Áo thun cotton mềm mại, phom suông tối giản dành cho trang phục hằng ngày.'),
        ('essential-cotton-tee', 'en',
         'Essential Cotton Tee', 'essential-cotton-tee',
         'A soft everyday cotton T-shirt with a relaxed fit.',
         'An everyday crew-neck T-shirt with relaxed shoulders and breathable cotton comfort.',
         '100% cotton', 'Machine wash cold and dry in the shade.',
         'Essential Cotton Tee',
         'A soft, relaxed cotton T-shirt designed for effortless everyday wear.'),

        ('urban-linen-dress', 'vi',
         'Đầm linen đô thị', 'dam-linen-do-thi',
         'Đầm linen nhẹ, thoáng mát cho những ngày nắng.',
         'Thiết kế đầm linen có phom suông mềm mại, thoải mái khi di chuyển và dễ phối phụ kiện.',
         'Linen pha', 'Giặt nhẹ bằng nước lạnh, không sấy nhiệt cao.',
         'Đầm linen đô thị',
         'Đầm linen nhẹ và thoáng mát với phom dáng hiện đại.'),
        ('urban-linen-dress', 'en',
         'Urban Linen Dress', 'urban-linen-dress',
         'A light linen dress made for warm city days.',
         'A relaxed linen dress with fluid movement, breathable comfort and effortless styling.',
         'Linen blend', 'Gentle cold wash; avoid high-heat drying.',
         'Urban Linen Dress',
         'A breathable linen dress with a relaxed modern silhouette.'),

        ('north-utility-jacket', 'vi',
         'Áo blazer North may cấu trúc', 'ao-blazer-north-may-cau-truc',
         'Blazer may đo có cấu trúc với phom một nút tối giản.',
         'Thiết kế blazer có vai đứng vừa phải, đường cắt gọn và phom một nút thanh lịch.',
         'Vải suit pha', 'Giặt khô để giữ cấu trúc áo.',
         'Áo blazer North may cấu trúc',
         'Blazer một nút hiện đại với phom may đo gọn gàng.'),
        ('north-utility-jacket', 'en',
         'North Structured Blazer', 'north-structured-blazer',
         'A structured tailored blazer with a clean single-button silhouette.',
         'A refined blazer with softly structured shoulders, precise seams and a minimal single-button front.',
         'Suiting blend', 'Dry clean to preserve the structure.',
         'North Structured Blazer',
         'A modern single-button blazer with clean structured tailoring.'),

        ('studio-canvas-tote', 'vi',
         'Túi tote da Studio', 'tui-tote-da-studio',
         'Túi tote da hạt cao cấp dành cho nhu cầu sử dụng hằng ngày.',
         'Túi tote rộng rãi với bề mặt da hạt, quai chắc chắn và ngăn trong tiện dụng.',
         'Da thật', 'Lau bằng khăn mềm và bảo quản nơi khô thoáng.',
         'Túi tote da Studio',
         'Túi tote da hạt cao cấp, rộng rãi và phù hợp để sử dụng mỗi ngày.'),
        ('studio-canvas-tote', 'en',
         'Studio Leather Tote', 'studio-leather-tote',
         'A premium pebbled-leather tote for daily carry.',
         'A spacious tote with pebbled leather, durable handles and a practical interior compartment.',
         'Genuine leather', 'Wipe with a soft cloth and store in a dry place.',
         'Studio Leather Tote',
         'A spacious premium leather tote designed for everyday use.'),

        ('tailored-black-trousers', 'vi',
         'Quần tây đen may đo', 'quan-tay-den-may-do',
         'Quần tây đen ống đứng với đường ly sắc nét.',
         'Thiết kế quần tây len pha, phom ống đứng và cạp may đo gọn gàng.',
         'Len pha', 'Giặt khô để giữ phom.',
         'Quần tây đen may đo',
         'Quần tây đen ống đứng thanh lịch với hệ size chữ S đến XL.'),
        ('tailored-black-trousers', 'en',
         'Tailored Black Trousers', 'tailored-black-trousers',
         'Straight-leg black trousers with crisp front pleats.',
         'Tailored wool-blend trousers with a clean waistband, straight leg and refined front pleats.',
         'Wool blend', 'Dry clean to preserve the tailored shape.',
         'Tailored Black Trousers',
         'Refined straight-leg black trousers available in apparel sizes S to XL.'),

        ('minimal-white-leather-sneakers', 'vi',
         'Giày sneaker da trắng tối giản', 'giay-sneaker-da-trang-toi-gian',
         'Giày sneaker da trắng cổ thấp, dễ phối đồ.',
         'Thiết kế sneaker tối giản với thân da mềm, lót êm và đế cao su bền.',
         'Da và cao su', 'Lau sạch bằng khăn mềm, tránh ngâm nước.',
         'Giày sneaker da trắng tối giản',
         'Giày sneaker da trắng tối giản với hệ size số 39 đến 42.'),
        ('minimal-white-leather-sneakers', 'en',
         'Minimal White Leather Sneakers', 'minimal-white-leather-sneakers',
         'Minimal low-top white leather sneakers for effortless styling.',
         'Clean low-top sneakers with soft leather uppers, cushioned lining and durable rubber soles.',
         'Leather and rubber', 'Wipe clean with a soft cloth; do not soak.',
         'Minimal White Leather Sneakers',
         'Minimal white leather sneakers available in numeric sizes 39 to 42.')
), product_entity AS (
    SELECT DISTINCT ON (fixture_key.base_slug)
        fixture_key.base_slug,
        product.id
    FROM (SELECT DISTINCT base_slug FROM product_fixture) fixture_key
    JOIN products product
      ON product.slug = fixture_key.base_slug
      OR EXISTS (
          SELECT 1
          FROM product_translations existing_translation
          JOIN product_fixture fixture_alias
            ON fixture_alias.base_slug = fixture_key.base_slug
           AND fixture_alias.localized_slug = existing_translation.slug
          WHERE existing_translation.product_id = product.id
      )
    ORDER BY
        fixture_key.base_slug,
        CASE WHEN product.slug = fixture_key.base_slug THEN 0 ELSE 1 END,
        product.id
)
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
    product.id,
    fixture.locale_code,
    fixture.name,
    fixture.localized_slug,
    fixture.short_description,
    fixture.description,
    fixture.material,
    fixture.care_instruction,
    fixture.seo_title,
    fixture.seo_description
FROM product_fixture fixture
JOIN product_entity product ON product.base_slug = fixture.base_slug
JOIN locales locale ON locale.code = fixture.locale_code
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
