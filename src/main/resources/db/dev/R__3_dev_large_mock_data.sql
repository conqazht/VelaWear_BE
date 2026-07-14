-- Clean up existing mock products/variants/categories/brands to ensure repeatable migrations
DELETE FROM review_images WHERE review_id IN (
    SELECT id FROM reviews WHERE order_item_id IN (
        SELECT id FROM order_items WHERE variant_id IN (
            SELECT id FROM product_variants WHERE product_id IN (
                SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
            )
        )
    )
);

DELETE FROM reviews WHERE order_item_id IN (
    SELECT id FROM order_items WHERE variant_id IN (
        SELECT id FROM product_variants WHERE product_id IN (
            SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
        )
    )
);

DELETE FROM order_items WHERE variant_id IN (
    SELECT id FROM product_variants WHERE product_id IN (
        SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
    )
);

DELETE FROM cart_items WHERE variant_id IN (
    SELECT id FROM product_variants WHERE product_id IN (
        SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
    )
);

DELETE FROM inventory_logs WHERE variant_id IN (
    SELECT id FROM product_variants WHERE product_id IN (
        SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
    )
);

DELETE FROM product_images WHERE product_id IN (
    SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
);

DELETE FROM product_translations WHERE product_id IN (
    SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
);

DELETE FROM product_attributes WHERE product_id IN (
    SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
);

DELETE FROM wishlists WHERE product_id IN (
    SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
);

DELETE FROM product_variants WHERE product_id IN (
    SELECT id FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
);

DELETE FROM products WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote');

-- Clean categories
DELETE FROM category_translations WHERE category_id IN (
    SELECT id FROM categories WHERE slug NOT IN ('men', 'women', 't-shirts', 'dresses', 'accessories', 'outerwear', 'jackets')
);

DELETE FROM categories WHERE slug NOT IN ('men', 'women', 't-shirts', 'dresses', 'accessories', 'outerwear', 'jackets') AND parent_id IS NOT NULL;
DELETE FROM categories WHERE slug NOT IN ('men', 'women', 't-shirts', 'dresses', 'accessories', 'outerwear', 'jackets');

-- Clean brands (keeping the 4 static ones: velawear, urban-thread, north-stitch, studio-v)
DELETE FROM brands WHERE slug NOT IN ('velawear', 'urban-thread', 'north-stitch', 'studio-v');

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

-- 3. Products Seeding (100 products distributed across categories)
DO $$
DECLARE
    ao_names TEXT[] := ARRAY[
        'Classic Linen Shirt', 'Oversized Cotton Tee', 'Silk Button-down Blouse', 
        'Ribbed Cotton Tank', 'Merino Wool Sweater', 'Hemp Pocket Tee', 
        'Striped Oxford Shirt', 'Knit Polo Shirt', 'Cropped Linen Top', 
        'Waffle Knit Henley', 'Chambray Work Shirt', 'V-Neck Silk Camisole', 
        'French Terry Sweatshirt', 'Mock Neck Long Sleeve', 'Artisan Band Collar Shirt'
    ];
    quan_names TEXT[] := ARRAY[
        'Pleated Wool Trousers', 'Relaxed Linen Pants', 'Slim Fit Chinos', 
        'Raw Denim Jeans', 'Wide Leg Linen Trousers', 'Drawstring Sweatpants', 
        'Tailored City Shorts', 'Cargo Utility Pants', 'Culotte Linen Pants', 
        'Straight Leg Corduroys', 'Structured Ponte Pants', 'Linen Drawstring Shorts', 
        'Tapered Ankle Pants', 'Wool Flannel Trousers', 'Artisan Crop Pants'
    ];
    vay_names TEXT[] := ARRAY[
        'Pleated Midi Skirt', 'Silk Slip Skirt', 'Linen Wrap Skirt', 
        'Denim Mini Skirt', 'A-Line Wool Skirt', 'Tiered Cotton Maxi Skirt', 
        'Knit Pencil Skirt', 'Utility Cargo Skirt', 'Satin Bias-Cut Skirt', 
        'Artisan Button-Front Skirt'
    ];
    dam_names TEXT[] := ARRAY[
        'Linen Slip Dress', 'Knit Midi Dress', 'Silk Wrap Maxi Dress', 
        'Cotton Tiered Sundress', 'Structured Shift Dress', 'Velvet Evening Gown', 
        'Ribbed Sweater Dress', 'Floral Georgette Dress', 'Minimalist T-Shirt Dress', 
        'Artisan Kaftan Dress', 'Utility Shirt Dress', 'Sleeveless Column Dress', 
        'French Linen Apron Dress', 'Asymmetrical Drape Dress', 'Long Sleeve Silk Dress'
    ];
    ao_khoac_names TEXT[] := ARRAY[
        'Classic Double-Breasted Blazer', 'Organic Cotton Denim Jacket', 'Wool Trench Coat', 
        'Nylon Bomber Jacket', 'Merino Cardigan Overcoat', 'Water-Resistant Parka', 
        'Suede Utility Jacket', 'Chunky Knit Cardigan', 'Minimalist Coach Jacket', 
        'Tailored Linen Blazer', 'Puffer Down Jacket', 'Mac Coat', 
        'Leather Biker Jacket', 'Shearling Aviator Jacket', 'Cropped Tweed Jacket'
    ];
    giay_names TEXT[] := ARRAY[
        'Air Force Leather Sneakers', 'Minimalist White Court Shoes', 'Leather Loafers', 
        'Chelsea Boots', 'Canvas Low-Top Sneakers', 'Suede Derby Shoes', 
        'Leather Monk Strap Shoes', 'Knit Running Shoes', 'Leather Ankle Boots', 
        'Minimalist Leather Sandals', 'Runner Knit Trail Shoes', 'Slip-on Canvas Mules', 
        'Velvet Loafers', 'Classic Wingtip Oxfords', 'Platform Leather Brogues'
    ];
    phu_kien_names TEXT[] := ARRAY[
        'Structured Leather Belt', 'Silk Scarf', 'Wool Beanie', 
        'Canvas Tote Bag', 'Classic Sunglasses', 'Silver Signet Ring', 
        'Leather Card Holder', 'Ribbed Cotton Socks', 'Minimalist Wristwatch', 
        'Linen Market Bag', 'Silver Chain Necklace', 'Cashmere Travel Wrap', 
        'Leather Backpack', 'Brass Key Hook', 'Wool Fedora Hat'
    ];
    
    cat_id BIGINT;
    br_id BIGINT;
    prod_name VARCHAR(150);
    prod_slug VARCHAR(150);
    i INT;
BEGIN
    -- Insert Áo products
    SELECT id INTO cat_id FROM categories WHERE slug = 'ao';
    FOR i IN 1..cardinality(ao_names) LOOP
        prod_name := ao_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Quần products
    SELECT id INTO cat_id FROM categories WHERE slug = 'quan';
    FOR i IN 1..cardinality(quan_names) LOOP
        prod_name := quan_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Váy products
    SELECT id INTO cat_id FROM categories WHERE slug = 'vay';
    FOR i IN 1..cardinality(vay_names) LOOP
        prod_name := vay_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Đầm products
    SELECT id INTO cat_id FROM categories WHERE slug = 'dam';
    FOR i IN 1..cardinality(dam_names) LOOP
        prod_name := dam_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Áo khoác products
    SELECT id INTO cat_id FROM categories WHERE slug = 'ao-khoac';
    FOR i IN 1..cardinality(ao_khoac_names) LOOP
        prod_name := ao_khoac_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Giày products
    SELECT id INTO cat_id FROM categories WHERE slug = 'giay';
    FOR i IN 1..cardinality(giay_names) LOOP
        prod_name := giay_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;

    -- Insert Phụ kiện products
    SELECT id INTO cat_id FROM categories WHERE slug = 'phu-kien';
    FOR i IN 1..cardinality(phu_kien_names) LOOP
        prod_name := phu_kien_names[i];
        prod_slug := lower(replace(prod_name, ' ', '-'));
        SELECT id INTO br_id FROM brands ORDER BY RANDOM() LIMIT 1;
        INSERT INTO products (name, slug, description, category_id, brand_id, status)
        VALUES (prod_name, prod_slug, 'Premium ' || prod_name || ' with fine stitching and sustainable design.', cat_id, br_id, 'ACTIVE')
        ON CONFLICT (slug) DO NOTHING;
    END LOOP;
END $$;

-- 4. Product Translations Seeding (Vietnamese translated names matching categories)
INSERT INTO product_translations (
    product_id, locale_code, name, slug, short_description, description, material, care_instruction, seo_title, seo_description
)
SELECT
    id,
    'vi',
    CASE name
        -- Áo (Shirts/Tops)
        WHEN 'Classic Linen Shirt' THEN 'Áo sơ mi linen cổ điển'
        WHEN 'Oversized Cotton Tee' THEN 'Áo thun cotton dáng rộng'
        WHEN 'Silk Button-down Blouse' THEN 'Áo sơ mi lụa cao cấp'
        WHEN 'Ribbed Cotton Tank' THEN 'Áo ba lỗ cotton gân'
        WHEN 'Merino Wool Sweater' THEN 'Áo len Merino tự nhiên'
        WHEN 'Hemp Pocket Tee' THEN 'Áo thun vải gai túi ngực'
        WHEN 'Striped Oxford Shirt' THEN 'Áo sơ mi kẻ sọc Oxford'
        WHEN 'Knit Polo Shirt' THEN 'Áo thun Polo dệt kim'
        WHEN 'Cropped Linen Top' THEN 'Áo croptop linen tinh tế'
        WHEN 'Waffle Knit Henley' THEN 'Áo thun cổ nút Waffle'
        WHEN 'Chambray Work Shirt' THEN 'Áo sơ mi vải Chambray'
        WHEN 'V-Neck Silk Camisole' THEN 'Áo hai dây lụa cổ V'
        WHEN 'French Terry Sweatshirt' THEN 'Áo nỉ French Terry'
        WHEN 'Mock Neck Long Sleeve' THEN 'Áo tay dài cổ lọ thấp'
        WHEN 'Artisan Band Collar Shirt' THEN 'Áo sơ mi cổ tàu thủ công'
        -- Quần (Pants/Trousers)
        WHEN 'Pleated Wool Trousers' THEN 'Quần tây xếp ly vải len'
        WHEN 'Relaxed Linen Pants' THEN 'Quần linen phom rộng'
        WHEN 'Slim Fit Chinos' THEN 'Quần Chino phom ôm nhẹ'
        WHEN 'Raw Denim Jeans' THEN 'Quần jeans raw denim cổ điển'
        WHEN 'Wide Leg Linen Trousers' THEN 'Quần linen ống rộng rủ'
        WHEN 'Drawstring Sweatpants' THEN 'Quần nỉ bo gấu dây rút'
        WHEN 'Tailored City Shorts' THEN 'Quần short tây thanh lịch'
        WHEN 'Cargo Utility Pants' THEN 'Quần túi hộp tiện ích'
        WHEN 'Culotte Linen Pants' THEN 'Quần lửng culottes linen'
        WHEN 'Straight Leg Corduroys' THEN 'Quần nhung tăm ống đứng'
        WHEN 'Structured Ponte Pants' THEN 'Quần ôm Ponte định hình'
        WHEN 'Linen Drawstring Shorts' THEN 'Quần short linen dây rút'
        WHEN 'Tapered Ankle Pants' THEN 'Quần tây ống côn thời thượng'
        WHEN 'Wool Flannel Trousers' THEN 'Quần dạ flannel ấm áp'
        WHEN 'Artisan Crop Pants' THEN 'Quần lửng dệt thủ công'
        -- Váy (Skirts)
        WHEN 'Pleated Midi Skirt' THEN 'Chân váy xếp ly dáng lửng'
        WHEN 'Silk Slip Skirt' THEN 'Chân váy lụa suông mềm'
        WHEN 'Linen Wrap Skirt' THEN 'Chân váy linen đắp chéo'
        WHEN 'Denim Mini Skirt' THEN 'Chân váy jeans ngắn'
        WHEN 'A-Line Wool Skirt' THEN 'Chân váy dạ chữ A'
        WHEN 'Tiered Cotton Maxi Skirt' THEN 'Chân váy maxi cotton nhiều tầng'
        WHEN 'Knit Pencil Skirt' THEN 'Chân váy bút chì dệt kim'
        WHEN 'Utility Cargo Skirt' THEN 'Chân váy túi hộp tiện dụng'
        WHEN 'Satin Bias-Cut Skirt' THEN 'Chân váy satin cắt xéo rủ'
        WHEN 'Artisan Button-Front Skirt' THEN 'Chân váy nút trước thủ công'
        -- Đầm (Dresses)
        WHEN 'Linen Slip Dress' THEN 'Đầm hai dây linen dáng suông'
        WHEN 'Knit Midi Dress' THEN 'Đầm dệt kim ôm nhẹ'
        WHEN 'Silk Wrap Maxi Dress' THEN 'Đầm lụa đắp chéo dáng dài'
        WHEN 'Cotton Tiered Sundress' THEN 'Đầm hai dây cotton nhiều tầng'
        WHEN 'Structured Shift Dress' THEN 'Đầm suông phom đứng'
        WHEN 'Velvet Evening Gown' THEN 'Đầm dạ hội nhung sang trọng'
        WHEN 'Ribbed Sweater Dress' THEN 'Đầm len gân ấm áp'
        WHEN 'Floral Georgette Dress' THEN 'Đầm voan hoa Georgette'
        WHEN 'Minimalist T-Shirt Dress' THEN 'Đầm thun suông tối giản'
        WHEN 'Artisan Kaftan Dress' THEN 'Đầm Kaftan dệt thủ công'
        WHEN 'Utility Shirt Dress' THEN 'Đầm sơ mi tiện dụng'
        WHEN 'Sleeveless Column Dress' THEN 'Đầm ôm không tay dáng cột'
        WHEN 'French Linen Apron Dress' THEN 'Đầm yếm French Linen'
        WHEN 'Asymmetrical Drape Dress' THEN 'Đầm rủ bất đối xứng'
        WHEN 'Long Sleeve Silk Dress' THEN 'Đầm lụa tay dài thanh lịch'
        -- Áo khoác (Jackets/Coats)
        WHEN 'Classic Double-Breasted Blazer' THEN 'Áo blazer hai hàng khuy cổ điển'
        WHEN 'Organic Cotton Denim Jacket' THEN 'Áo khoác jeans cotton hữu cơ'
        WHEN 'Wool Trench Coat' THEN 'Áo măng tô len dáng dài'
        WHEN 'Nylon Bomber Jacket' THEN 'Áo khoác bomber nylon'
        WHEN 'Merino Cardigan Overcoat' THEN 'Áo khoác cardigan len Merino'
        WHEN 'Water-Resistant Parka' THEN 'Áo khoác phao chống nước'
        WHEN 'Suede Utility Jacket' THEN 'Áo khoác da lộn tiện ích'
        WHEN 'Chunky Knit Cardigan' THEN 'Áo khoác cardigan len dày'
        WHEN 'Minimalist Coach Jacket' THEN 'Áo khoác gió tối giản'
        WHEN 'Tailored Linen Blazer' THEN 'Áo blazer linen may đo tinh tế'
        WHEN 'Puffer Down Jacket' THEN 'Áo khoác phao lông vũ'
        WHEN 'Mac Coat' THEN 'Áo khoác dáng dài Mac'
        WHEN 'Leather Biker Jacket' THEN 'Áo khoác da biker cá tính'
        WHEN 'Shearling Aviator Jacket' THEN 'Áo khoác da lót lông phi công'
        WHEN 'Cropped Tweed Jacket' THEN 'Áo khoác tweed dáng ngắn'
        -- Giày (Shoes)
        WHEN 'Air Force Leather Sneakers' THEN 'Giày thể thao da Air Force'
        WHEN 'Minimalist White Court Shoes' THEN 'Giày thể thao trắng tối giản'
        WHEN 'Leather Loafers' THEN 'Giày lười da cao cấp'
        WHEN 'Chelsea Boots' THEN 'Giày boot Chelsea thanh lịch'
        WHEN 'Canvas Low-Top Sneakers' THEN 'Giày thể thao vải cổ thấp'
        WHEN 'Suede Derby Shoes' THEN 'Giày tây da lộn Derby'
        WHEN 'Leather Monk Strap Shoes' THEN 'Giày tây khóa quai da'
        WHEN 'Knit Running Shoes' THEN 'Giày chạy bộ dệt kim thoáng khí'
        WHEN 'Leather Ankle Boots' THEN 'Giày boot da cổ ngắn'
        WHEN 'Minimalist Leather Sandals' THEN 'Sandal da tối giản'
        WHEN 'Runner Knit Trail Shoes' THEN 'Giày chạy bộ dã ngoại dệt kim'
        WHEN 'Slip-on Canvas Mules' THEN 'Giày sục vải canvas'
        WHEN 'Velvet Loafers' THEN 'Giày lười nhung sang trọng'
        WHEN 'Classic Wingtip Oxfords' THEN 'Giày tây Oxfords cổ điển'
        WHEN 'Platform Leather Brogues' THEN 'Giày đế xuồng da Brogues'
        -- Phụ kiện (Accessories)
        WHEN 'Structured Leather Belt' THEN 'Thắt lưng da thật phom đứng'
        WHEN 'Silk Scarf' THEN 'Khăn quàng lụa cao cấp'
        WHEN 'Wool Beanie' THEN 'Mũ len Merino ấm áp'
        WHEN 'Canvas Tote Bag' THEN 'Túi tote vải canvas bền bỉ'
        WHEN 'Classic Sunglasses' THEN 'Kính mát cổ điển chống UV'
        WHEN 'Silver Signet Ring' THEN 'Nhẫn bạc Signet nguyên chất'
        WHEN 'Leather Card Holder' THEN 'Ví đựng thẻ da thật'
        WHEN 'Ribbed Cotton Socks' THEN 'Vớ cotton tăm thoáng khí'
        WHEN 'Minimalist Wristwatch' THEN 'Đồng hồ đeo tay tối giản'
        WHEN 'Linen Market Bag' THEN 'Túi đi chợ linen mộc mạc'
        WHEN 'Silver Chain Necklace' THEN 'Dây chuyền xích bạc'
        WHEN 'Cashmere Travel Wrap' THEN 'Khăn choàng du lịch Cashmere'
        WHEN 'Leather Backpack' THEN 'Balo da thật cao cấp'
        WHEN 'Brass Key Hook' THEN 'Móc chìa khóa bằng đồng'
        WHEN 'Wool Fedora Hat' THEN 'Mũ dạ Fedora cổ điển'
        ELSE name
    END,
    slug,
    'Thiết kế tối giản, chất liệu cao cấp cho phong cách thời thượng.',
    'Sản phẩm được chế tạo tỉ mỉ từ những sợi vải tự nhiên, thân thiện với môi trường, phom dáng rủ tự nhiên tôn lên nét quyến rũ tĩnh lặng.',
    CASE
        WHEN category_id = (SELECT id FROM categories WHERE slug = 'giay') THEN 'Da / Cao su'
        WHEN category_id = (SELECT id FROM categories WHERE slug = 'phu-kien') THEN 'Da / Kim loại / Cotton'
        ELSE 'Linen / Cotton hữu cơ / Len'
    END,
    'Giặt nhẹ hoặc giặt khô để giữ sản phẩm bền lâu.',
    name,
    'Thiết kế tinh tế từ Vela Wear.'
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

-- 5. Product Variants (Seeding variants matching sizes rules)
DO $$
DECLARE
    prod_rec RECORD;
    col_id BIGINT;
    sz_id BIGINT;
    i INT;
    var_count INT := 0;
BEGIN
    FOR prod_rec IN (
        SELECT id, category_id FROM products 
        WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
    ) LOOP
        -- Generate 2-3 variants for each product
        FOR i IN 1..(2 + (FLOOR(RANDOM() * 2))::INT) LOOP
            SELECT id INTO col_id FROM colors ORDER BY RANDOM() LIMIT 1;
            SELECT s.id INTO sz_id
            FROM sizes s
            WHERE CASE
                WHEN EXISTS (
                    SELECT 1 FROM categories c
                    WHERE c.id = prod_rec.category_id AND c.slug = 'phu-kien'
                ) THEN s.name IN ('ONE SIZE', 'ADJUSTABLE', 'REGULAR', 'LARGE')
                WHEN EXISTS (
                    SELECT 1 FROM categories c
                    WHERE c.id = prod_rec.category_id AND c.slug = 'giay'
                ) THEN s.name IN ('38', '39', '40', '41', '42', '43', '44')
                ELSE s.name IN ('XS', 'S', 'M', 'L', 'XL', 'XXL')
            END
            ORDER BY RANDOM()
            LIMIT 1;
            
            var_count := var_count + 1;
            
            INSERT INTO product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id, status)
            VALUES (
                prod_rec.id, 
                'SKU-' || prod_rec.id || '-' || var_count || '-' || FLOOR(RANDOM() * 1000)::INT, 
                150000.00 + (FLOOR(RANDOM() * 1000000)::INT), 
                NULL, 
                10 + (FLOOR(RANDOM() * 90))::INT, 
                col_id, 
                sz_id, 
                'ACTIVE'
            )
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

-- 6. Product Images (Assigning category-aware premium images to products)
DO $$
DECLARE
    prod_rec RECORD;
    idx INT;
    
    ao_imgs TEXT[] := ARRAY[
        '/uploads/products/detail_shot_of_the_ribbed_hem_and_stitching_on_a_charcoal_gray_808080_cashmere.png',
        '/uploads/products/extreme_macro_close_up_shot_of_the_charcoal_gray_808080_soft_cashmere_knit.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_charcoal_gray_808080_cashmere.png',
        '/uploads/products/high_resolution_individual_product_shot_of_a_charcoal_gray_808080_cashmere.png',
        '/uploads/products/individual_product_shot_of_the_back_view_of_a_charcoal_gray_808080_cashmere.png',
        '/uploads/products/side_profile_shot_of_a_male_model_wearing_a_charcoal_gray_808080_cashmere.png',
        '/uploads/products/individual_product_shot_of_a_luxury_olive_green_556b2f_cashmere_sweater_front.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_olive_green_556b2f_cashmere.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_luxury_olive_green_556b2f_cashmere.png',
        '/uploads/products/product_detail_gallery_set_for_an_olive_green_556b2f_cashmere_sweater._4.png',
        '/uploads/products/macro_shot_of_the_olive_green_556b2f_soft_cashmere_knit_weave._high_end.png',
        '/uploads/products/premium_fashion_product_shot_of_a_high_quality_tailored_piece_in_olive_green.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_crisp_white_ffffff_linen_shirt_and.png',
        '/uploads/products/artisanal_fashion_product_shot_for_vela_wear._a_premium_garment_in_crisp_white.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_artisanal_crisp_white_ffffff_linen.png'
    ];
    
    quan_imgs TEXT[] := ARRAY[
        '/uploads/products/full_product_detail_gallery_set_for_a_single_luxury_minimalist_black_000000.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_minimalist_terracotta_b5573a_linen.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_premium_terracotta_b5573a_linen.png',
        '/uploads/products/macro_shot_of_the_terracotta_b5573a_linen_fabric_texture_and_v_neckline_on_a.png'
    ];
    
    vay_imgs TEXT[] := ARRAY[
        '/uploads/products/full_product_detail_gallery_set_for_a_single_charcoal_gray_808080_silk_midi.png',
        '/uploads/products/high_resolution_individual_product_shot_of_a_charcoal_gray_808080_silk_midi.png',
        '/uploads/products/individual_product_shot_of_the_back_view_of_a_charcoal_gray_808080_silk_midi.png',
        '/uploads/products/macro_close_up_shot_of_charcoal_gray_808080_silk_fabric_texture_showing_the.png',
        '/uploads/products/product_detail_gallery_set_for_a_charcoal_gray_808080_silk_midi_dress._4.png',
        '/uploads/products/detail_shot_of_the_hemline_and_flowing_silhouette_of_a_charcoal_gray_808080.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_sand_beige_d8cab8_artisanal_dress.png',
        '/uploads/products/individual_product_shot_of_a_premium_terracotta_b5573a_linen_jumpsuit_front.png'
    ];
    
    ao_khoac_imgs TEXT[] := ARRAY[
        '/uploads/products/full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_blazer.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_suit_1.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_suit_2.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_wool.png',
        '/uploads/products/high_resolution_individual_product_shot_of_a_structured_deep_navy_1f3a5f_wool.png',
        '/uploads/products/individual_product_shot_of_a_male_model_wearing_a_structured_deep_navy_1f3a5f.png',
        '/uploads/products/individual_product_shot_of_a_structured_deep_navy_1f3a5f_linen_wool_blazer_on_a.png',
        '/uploads/products/individual_product_shot_of_the_back_view_of_a_structured_deep_navy_1f3a5f_wool.png',
        '/uploads/products/macro_detail_shot_of_the_waist_belt_and_stitching_on_a_structured_deep_navy.png',
        '/uploads/products/macro_shot_of_the_deep_navy_1f3a5f_linen_wool_blend_fabric_texture_on_a_blazer_.png',
        '/uploads/products/product_detail_gallery_set_for_a_structured_deep_navy_1f3a5f_wool_jumpsuit._4.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_luxury_olive_green_556b2f_tailored.png',
        '/uploads/products/individual_product_shot_of_a_luxury_minimalist_sand_beige_d8cab8_linen_blazer.png',
        '/uploads/products/product_detail_gallery_set_for_a_luxury_minimalist_sand_beige_d8cab8_linen.png',
        '/uploads/products/professional_studio_photography_of_an_artisanal_garment_in_warm_sand_beige.png'
    ];
    
    giay_imgs TEXT[] := ARRAY[
        '/uploads/products/sneaker_af_1.png',
        '/uploads/products/sneaker_af_2.png',
        '/uploads/products/sneaker_af_3.png',
        '/uploads/products/sneaker_af_4.png',
        '/uploads/products/individual_product_shot_of_a_luxury_minimalist_black_000000_leather_loafer_side.png'
    ];
    
    phu_kien_imgs TEXT[] := ARRAY[
        '/uploads/products/key_hook_1.png',
        '/uploads/products/key_hook_2.png',
        '/uploads/products/key_hook_3.png',
        '/uploads/products/key_hook_4.png',
        '/uploads/products/luxury_accessory_product_shot_for_vela_wear._a_minimalist_bag_in_sand_beige.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_premium_black_000000_leather_bag.png',
        '/uploads/products/full_product_detail_gallery_set_for_a_single_premium_black_000000_leather_tote.png',
        '/uploads/products/individual_product_shot_of_a_premium_black_000000_leather_tote_bag_perspective.png',
        '/uploads/products/macro_close_up_shot_of_the_black_000000_pebbled_leather_texture_and_embossed.png',
        '/uploads/products/product_detail_gallery_set_for_a_premium_black_000000_leather_tote_bag._4.png',
        '/uploads/products/professional_studio_product_photography_for_vela_wear._a_minimalist_leather.png',
        '/uploads/products/professional_studio_product_photography_for_vela_wear._minimalist_leather.png',
        '/uploads/products/professional_studio_product_photography_of_a_luxury_minimalist_accessory_in.png'
    ];

    selected_imgs TEXT[];
    img_count INT;
BEGIN
    idx := 0;
    FOR prod_rec IN (
        SELECT id, slug, (SELECT slug FROM categories WHERE id = category_id) as cat_slug 
        FROM products 
        WHERE slug NOT IN ('essential-cotton-tee', 'urban-linen-dress', 'north-utility-jacket', 'studio-canvas-tote')
    ) LOOP
        idx := idx + 1;
        
        -- Select image array based on category slug
        IF prod_rec.cat_slug = 'shirts-tops' THEN
            selected_imgs := ao_imgs;
        ELSIF prod_rec.cat_slug = 'pants-trousers' THEN
            selected_imgs := quan_imgs;
        ELSIF prod_rec.cat_slug = 'skirts' THEN
            selected_imgs := vay_imgs;
        ELSIF prod_rec.cat_slug = 'dresses' THEN
            selected_imgs := vay_imgs; -- reuse vay/dam
        ELSIF prod_rec.cat_slug = 'jackets-coats' THEN
            selected_imgs := ao_khoac_imgs;
        ELSIF prod_rec.cat_slug = 'shoes' THEN
            selected_imgs := giay_imgs;
        ELSE
            selected_imgs := phu_kien_imgs;
        END IF;

        -- Specific matches for perfect accuracy
        IF prod_rec.slug = 'air-force-leather-sneakers' THEN
            selected_imgs := ARRAY[
                '/uploads/products/sneaker_af_1.png',
                '/uploads/products/sneaker_af_2.png',
                '/uploads/products/sneaker_af_3.png',
                '/uploads/products/sneaker_af_4.png'
            ];
        ELSIF prod_rec.slug = 'brass-key-hook' THEN
            selected_imgs := ARRAY[
                '/uploads/products/key_hook_1.png',
                '/uploads/products/key_hook_2.png',
                '/uploads/products/key_hook_3.png',
                '/uploads/products/key_hook_4.png'
            ];
        END IF;

        img_count := array_length(selected_imgs, 1);
        
        -- Seed 4 detail images for this product
        -- Image 1: Thumbnail
        INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
        VALUES (prod_rec.id, NULL, selected_imgs[((idx * 4 - 3) % img_count) + 1], TRUE, 1)
        ON CONFLICT DO NOTHING;
        
        -- Image 2
        INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
        VALUES (prod_rec.id, NULL, selected_imgs[((idx * 4 - 2) % img_count) + 1], FALSE, 2)
        ON CONFLICT DO NOTHING;
        
        -- Image 3
        INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
        VALUES (prod_rec.id, NULL, selected_imgs[((idx * 4 - 1) % img_count) + 1], FALSE, 3)
        ON CONFLICT DO NOTHING;
        
        -- Image 4
        INSERT INTO product_images (product_id, variant_id, image, is_thumbnail, sort_order)
        VALUES (prod_rec.id, NULL, selected_imgs[((idx * 4) % img_count) + 1], FALSE, 4)
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- 7. Product Attributes (100 records)
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

-- 8. User Addresses (100 records)
DO $$
DECLARE
    i INT;
    u_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO u_id FROM users ORDER BY RANDOM() LIMIT 1;
        IF u_id IS NOT NULL THEN
            INSERT INTO user_addresses (user_id, receiver_name, phone, province, ward, address_detail, is_default)
            VALUES (u_id, 'Receiver ' || i, '09' || LPAD(i::text, 8, '0'), 'Province ' || i, 'Ward ' || i, 'Address Detail ' || i, FALSE)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 9. Carts (Ensure all users have a cart)
INSERT INTO carts (user_id)
SELECT id FROM users
ON CONFLICT (user_id) DO NOTHING;

-- 10. Cart Items (100 records)
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

-- 11. Coupons (100 records)
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

-- 12. Orders (100 records)
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

-- 13. Order Items (120 records)
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

-- 14. Payments (100 records)
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

-- 15. Payment Transactions (100 records)
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

-- 16. Coupon Usages (100 records)
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

-- 17. Reviews (100 records)
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
            VALUES (u_id, oi_id, (i % 5) + 1, 'Review for product: ' || oi_id)
            ON CONFLICT (user_id, order_item_id) DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 18. Review Images (100 records)
DO $$
DECLARE
    i INT;
    rev_id BIGINT;
BEGIN
    FOR i IN 1..100 LOOP
        SELECT id INTO rev_id FROM reviews ORDER BY RANDOM() LIMIT 1;
        IF rev_id IS NOT NULL THEN
            INSERT INTO review_images (review_id, image)
            VALUES (rev_id, CASE WHEN i % 2 = 0 THEN '/uploads/reviews/essential-cotton-tee-review.png' ELSE '/uploads/reviews/north-utility-jacket-review.png' END)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 19. Inventory Logs (100 records)
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
        END If;
    END LOOP;
END $$;

-- 20. Order Status Histories (100 records)
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

-- 21. Wishlists (100 records)
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
