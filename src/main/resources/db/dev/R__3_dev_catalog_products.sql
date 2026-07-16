-- Deterministic development storefront catalog.
-- Contract: exactly 100 owned Product rows; colors are Variant dimensions and
-- never count as additional products. Every product-color has at least three
-- dedicated images, and every PNG that existed in uploads/products is reused.
-- Keep this repeatable migration lexically before R__3_dev_large_mock_data.sql.

-- Categories used by the normalized storefront catalog.
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
SET name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    status = EXCLUDED.status,
    sort_order = EXCLUDED.sort_order;

INSERT INTO colors (name, hex_code, sort_order)
VALUES
    ('Black', '#000000', 1),
    ('Red', '#D32F2F', 2),
    ('Yellow', '#F2C94C', 3),
    ('White', '#FFFFFF', 4),
    ('Purple', '#7B2CBF', 5),
    ('Orange', '#F97316', 6),
    ('Charcoal Gray', '#808080', 7),
    ('Deep Navy', '#1F3A5F', 8),
    ('Olive Green', '#556B2F', 9),
    ('Terracotta', '#B5573A', 10),
    ('Sand Beige', '#D8CAB8', 11),
    ('Brass', '#B08D57', 12),
    ('Brown', '#8B5E3C', 13),
    ('Ivory', '#F5F0E6', 14),
    ('Dark Brown', '#3B2417', 15)
ON CONFLICT (name) DO UPDATE
SET hex_code = EXCLUDED.hex_code,
    sort_order = EXCLUDED.sort_order;

DROP TABLE IF EXISTS dev_catalog_product_fixture;
CREATE TEMP TABLE dev_catalog_product_fixture (
    position INTEGER PRIMARY KEY,
    slug VARCHAR(280) UNIQUE NOT NULL,
    sku_prefix VARCHAR(100) UNIQUE NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    name_vi VARCHAR(255) NOT NULL,
    category_slug VARCHAR(280) NOT NULL,
    size_family VARCHAR(20) NOT NULL,
    primary_color VARCHAR(80) NOT NULL,
    CONSTRAINT ck_dev_catalog_position CHECK (position BETWEEN 1 AND 100),
    CONSTRAINT ck_dev_catalog_size_family CHECK (
        size_family IN ('APPAREL', 'SHOES', 'ONE_SIZE', 'ADJUSTABLE')
    )
);

INSERT INTO dev_catalog_product_fixture (
    position, slug, sku_prefix, name_en, name_vi,
    category_slug, size_family, primary_color
)
VALUES
    (1, 'crisp-white-linen-blazer', 'DEV-CAT-001', 'Crisp White Linen Blazer', 'Blazer linen trắng tinh', 'ao-khoac', 'APPAREL', 'White'),
    (2, 'essential-cotton-tee', 'DEV-CAT-002', 'Essential Cotton Tee', 'Áo thun cotton thiết yếu', 'ao', 'APPAREL', 'Black'),
    (3, 'cashmere-crewneck', 'DEV-CAT-003', 'Cashmere Crewneck', 'Áo len cashmere cổ tròn', 'ao', 'APPAREL', 'Charcoal Gray'),
    (4, 'crisp-white-linen-shirt', 'DEV-CAT-004', 'Crisp White Linen Shirt', 'Áo sơ mi linen trắng tinh', 'ao', 'APPAREL', 'White'),
    (5, 'terracotta-knit-lounge-set', 'DEV-CAT-005', 'Terracotta Knit Lounge Set', 'Bộ đồ lounge dệt kim đất nung', 'ao', 'APPAREL', 'Terracotta'),
    (6, 'womens-classic-tailored-suit', 'DEV-CAT-006', 'Women''s Classic Tailored Suit', 'Bộ suit nữ may đo cổ điển', 'ao-khoac', 'APPAREL', 'Deep Navy'),
    (7, 'womens-structured-pantsuit', 'DEV-CAT-007', 'Women''s Structured Pantsuit', 'Bộ pantsuit nữ phom cấu trúc', 'ao-khoac', 'APPAREL', 'Charcoal Gray'),
    (8, 'terracotta-linen-jumpsuit', 'DEV-CAT-008', 'Terracotta Linen Jumpsuit', 'Jumpsuit linen đất nung', 'dam', 'APPAREL', 'Terracotta'),
    (9, 'navy-wool-belted-jumpsuit', 'DEV-CAT-009', 'Navy Wool Belted Jumpsuit', 'Jumpsuit len thắt đai xanh navy', 'dam', 'APPAREL', 'Deep Navy'),
    (10, 'navy-wrap-tailored-jumpsuit', 'DEV-CAT-010', 'Navy Wrap Tailored Jumpsuit', 'Jumpsuit may đo cổ quấn xanh navy', 'dam', 'APPAREL', 'Deep Navy'),
    (11, 'charcoal-silk-midi-dress', 'DEV-CAT-011', 'Charcoal Silk Midi Dress', 'Đầm midi lụa xám than', 'dam', 'APPAREL', 'Charcoal Gray'),
    (12, 'urban-linen-dress', 'DEV-CAT-012', 'Artisan Linen Dress', 'Đầm linen thủ công', 'dam', 'APPAREL', 'Sand Beige'),
    (13, 'minimal-white-leather-sneakers', 'DEV-CAT-013', 'White Court Sneaker', 'Giày sneaker court trắng', 'giay', 'SHOES', 'White'),
    (14, 'black-leather-loafers', 'DEV-CAT-014', 'Black Leather Loafers', 'Giày loafer da đen', 'giay', 'SHOES', 'Black'),
    (15, 'sand-linen-blazer', 'DEV-CAT-015', 'Sand Linen Blazer', 'Blazer linen be cát', 'ao-khoac', 'APPAREL', 'Sand Beige'),
    (16, 'north-utility-jacket', 'DEV-CAT-016', 'North Structured Blazer', 'Blazer North phom cấu trúc', 'ao-khoac', 'APPAREL', 'Purple'),
    (17, 'black-hooded-linen-cape', 'DEV-CAT-017', 'Black Hooded Linen Cape', 'Áo cape linen có mũ đen', 'ao-khoac', 'APPAREL', 'Black'),
    (18, 'navy-pleated-cape-trouser-set', 'DEV-CAT-018', 'Navy Pleated Cape Trouser Set', 'Bộ cape xếp ly và quần navy', 'ao-khoac', 'APPAREL', 'Deep Navy'),
    (19, 'navy-mens-linen-wool-blazer', 'DEV-CAT-019', 'Men''s Navy Linen-Wool Blazer', 'Blazer nam linen-len navy', 'ao-khoac', 'APPAREL', 'Deep Navy'),
    (20, 'navy-mens-tailored-suit', 'DEV-CAT-020', 'Men''s Navy Tailored Suit', 'Bộ suit nam may đo navy', 'ao-khoac', 'APPAREL', 'Deep Navy'),
    (21, 'olive-wool-overcoat', 'DEV-CAT-021', 'Olive Wool Overcoat', 'Áo overcoat len olive', 'ao-khoac', 'APPAREL', 'Olive Green'),
    (22, 'tailored-black-trousers', 'DEV-CAT-022', 'Tailored Black Trousers', 'Quần tây đen may đo', 'quan', 'APPAREL', 'Black'),
    (23, 'studio-canvas-tote', 'DEV-CAT-023', 'Structured Leather Tote', 'Túi tote da phom cấu trúc', 'phu-kien', 'ONE_SIZE', 'Black'),
    (24, 'sand-minimal-leather-tote', 'DEV-CAT-024', 'Sand Minimal Leather Tote', 'Túi tote da tối giản be cát', 'phu-kien', 'ONE_SIZE', 'Sand Beige'),
    (25, 'black-crescent-shoulder-bag', 'DEV-CAT-025', 'Black Crescent Shoulder Bag', 'Túi đeo vai bán nguyệt đen', 'phu-kien', 'ONE_SIZE', 'Black'),
    (26, 'charcoal-top-handle-bag', 'DEV-CAT-026', 'Charcoal Top-Handle Bag', 'Túi xách tay phom hộp xám than', 'phu-kien', 'ONE_SIZE', 'Charcoal Gray'),
    (27, 'black-leather-clutch', 'DEV-CAT-027', 'Black Leather Clutch', 'Ví cầm tay da đen', 'phu-kien', 'ONE_SIZE', 'Black'),
    (28, 'sand-leather-wallet', 'DEV-CAT-028', 'Sand Leather Wallet', 'Ví da be cát', 'phu-kien', 'ONE_SIZE', 'Sand Beige'),
    (29, 'olive-zip-pouch-cardholder-set', 'DEV-CAT-029', 'Olive Zip Pouch & Cardholder Set', 'Bộ túi khóa kéo và ví thẻ olive', 'phu-kien', 'ONE_SIZE', 'Olive Green'),
    (30, 'white-sculptural-cuff', 'DEV-CAT-030', 'White Sculptural Cuff', 'Vòng cuff điêu khắc trắng', 'phu-kien', 'ADJUSTABLE', 'White'),
    (31, 'brass-ring-key-hook', 'DEV-CAT-031', 'Brass Ring Key Hook', 'Móc khóa vòng đồng thau', 'phu-kien', 'ONE_SIZE', 'Brass'),
    (32, 'brass-double-wall-hook', 'DEV-CAT-032', 'Brass Double Wall Hook', 'Móc tường đôi đồng thau', 'phu-kien', 'ONE_SIZE', 'Brass'),
    (33, 'leather-loop-key-hook', 'DEV-CAT-033', 'Leather Loop Key Hook', 'Móc khóa vòng da', 'phu-kien', 'ONE_SIZE', 'Brown'),
    (34, 'brass-key-rack', 'DEV-CAT-034', 'Brass Key Rack', 'Thanh treo khóa đồng thau', 'phu-kien', 'ONE_SIZE', 'Brass'),
    (35, 'pima-cotton-henley', 'DEV-CAT-035', 'Pima Cotton Henley', 'Áo Henley cotton Pima', 'ao', 'APPAREL', 'Deep Navy'),
    (36, 'ribbed-merino-turtleneck', 'DEV-CAT-036', 'Ribbed Merino Turtleneck', 'Áo cổ lọ Merino gân', 'ao', 'APPAREL', 'Charcoal Gray'),
    (37, 'silk-camp-collar-shirt', 'DEV-CAT-037', 'Silk Camp-Collar Shirt', 'Áo sơ mi lụa cổ Cuba', 'ao', 'APPAREL', 'Terracotta'),
    (38, 'linen-grandad-collar-shirt', 'DEV-CAT-038', 'Linen Grandad-Collar Shirt', 'Áo sơ mi linen cổ trụ', 'ao', 'APPAREL', 'White'),
    (39, 'organic-cotton-polo', 'DEV-CAT-039', 'Organic Cotton Polo', 'Áo polo cotton hữu cơ', 'ao', 'APPAREL', 'Olive Green'),
    (40, 'draped-silk-blouse', 'DEV-CAT-040', 'Draped Silk Blouse', 'Áo blouse lụa rủ', 'ao', 'APPAREL', 'Sand Beige'),
    (41, 'pleated-georgette-blouse', 'DEV-CAT-041', 'Pleated Georgette Blouse', 'Áo blouse Georgette xếp ly', 'ao', 'APPAREL', 'Ivory'),
    (42, 'cropped-boxy-linen-shirt', 'DEV-CAT-042', 'Cropped Boxy Linen Shirt', 'Áo sơ mi linen hộp dáng ngắn', 'ao', 'APPAREL', 'White'),
    (43, 'wool-silk-mockneck', 'DEV-CAT-043', 'Wool-Silk Mockneck', 'Áo cổ lọ thấp len-lụa', 'ao', 'APPAREL', 'Charcoal Gray'),
    (44, 'ribbed-cashmere-cardigan', 'DEV-CAT-044', 'Ribbed Cashmere Cardigan', 'Áo cardigan cashmere gân', 'ao', 'APPAREL', 'Sand Beige'),
    (45, 'linen-overshirt', 'DEV-CAT-045', 'Linen Overshirt', 'Áo khoác sơ mi linen', 'ao-khoac', 'APPAREL', 'Olive Green'),
    (46, 'relaxed-poplin-tunic', 'DEV-CAT-046', 'Relaxed Poplin Tunic', 'Áo tunic poplin dáng suông', 'ao', 'APPAREL', 'White'),
    (47, 'pleated-wool-trousers', 'DEV-CAT-047', 'Pleated Wool Trousers', 'Quần len xếp ly', 'quan', 'APPAREL', 'Charcoal Gray'),
    (48, 'wide-leg-linen-trousers', 'DEV-CAT-048', 'Wide-Leg Linen Trousers', 'Quần linen ống rộng', 'quan', 'APPAREL', 'Sand Beige'),
    (49, 'tapered-cotton-chinos', 'DEV-CAT-049', 'Tapered Cotton Chinos', 'Quần chinos cotton ống côn', 'quan', 'APPAREL', 'Olive Green'),
    (50, 'silk-drawstring-trousers', 'DEV-CAT-050', 'Silk Drawstring Trousers', 'Quần lụa dây rút', 'quan', 'APPAREL', 'Deep Navy'),
    (51, 'cropped-cigarette-trousers', 'DEV-CAT-051', 'Cropped Cigarette Trousers', 'Quần cigarette dáng lửng', 'quan', 'APPAREL', 'Black'),
    (52, 'utility-cargo-trousers', 'DEV-CAT-052', 'Utility Cargo Trousers', 'Quần cargo tiện ích', 'quan', 'APPAREL', 'Terracotta'),
    (53, 'bias-cut-silk-midi-skirt', 'DEV-CAT-053', 'Bias-Cut Silk Midi Skirt', 'Chân váy midi lụa cắt xéo', 'vay', 'APPAREL', 'Charcoal Gray'),
    (54, 'pleated-wool-midi-skirt', 'DEV-CAT-054', 'Pleated Wool Midi Skirt', 'Chân váy midi len xếp ly', 'vay', 'APPAREL', 'Deep Navy'),
    (55, 'linen-wrap-skirt', 'DEV-CAT-055', 'Linen Wrap Skirt', 'Chân váy linen đắp chéo', 'vay', 'APPAREL', 'Sand Beige'),
    (56, 'cotton-a-line-skirt', 'DEV-CAT-056', 'Cotton A-Line Skirt', 'Chân váy cotton chữ A', 'vay', 'APPAREL', 'White'),
    (57, 'leather-pencil-skirt', 'DEV-CAT-057', 'Leather Pencil Skirt', 'Chân váy bút chì da', 'vay', 'APPAREL', 'Black'),
    (58, 'draped-maxi-skirt', 'DEV-CAT-058', 'Draped Maxi Skirt', 'Chân váy maxi dáng rủ', 'vay', 'APPAREL', 'Olive Green'),
    (59, 'silk-slip-dress', 'DEV-CAT-059', 'Silk Slip Dress', 'Đầm lụa hai dây', 'dam', 'APPAREL', 'Black'),
    (60, 'linen-shirt-dress', 'DEV-CAT-060', 'Linen Shirt Dress', 'Đầm sơ mi linen', 'dam', 'APPAREL', 'Sand Beige'),
    (61, 'wool-column-dress', 'DEV-CAT-061', 'Wool Column Dress', 'Đầm len dáng cột', 'dam', 'APPAREL', 'Charcoal Gray'),
    (62, 'cotton-poplin-midi-dress', 'DEV-CAT-062', 'Cotton Poplin Midi Dress', 'Đầm midi poplin cotton', 'dam', 'APPAREL', 'White'),
    (63, 'wrap-jersey-dress', 'DEV-CAT-063', 'Wrap Jersey Dress', 'Đầm jersey đắp chéo', 'dam', 'APPAREL', 'Terracotta'),
    (64, 'pleated-chiffon-maxi-dress', 'DEV-CAT-064', 'Pleated Chiffon Maxi Dress', 'Đầm maxi chiffon xếp ly', 'dam', 'APPAREL', 'Deep Navy'),
    (65, 'knit-rib-midi-dress', 'DEV-CAT-065', 'Knit Rib Midi Dress', 'Đầm midi dệt kim gân', 'dam', 'APPAREL', 'Olive Green'),
    (66, 'belted-safari-dress', 'DEV-CAT-066', 'Belted Safari Dress', 'Đầm safari thắt đai', 'dam', 'APPAREL', 'Sand Beige'),
    (67, 'one-shoulder-evening-dress', 'DEV-CAT-067', 'One-Shoulder Evening Dress', 'Đầm dạ hội lệch vai', 'dam', 'APPAREL', 'Black'),
    (68, 'linen-apron-dress', 'DEV-CAT-068', 'Linen Apron Dress', 'Đầm yếm linen', 'dam', 'APPAREL', 'White'),
    (69, 'utility-cotton-jumpsuit', 'DEV-CAT-069', 'Utility Cotton Jumpsuit', 'Jumpsuit cotton tiện ích', 'dam', 'APPAREL', 'Olive Green'),
    (70, 'sleeveless-tailored-jumpsuit', 'DEV-CAT-070', 'Sleeveless Tailored Jumpsuit', 'Jumpsuit may đo không tay', 'dam', 'APPAREL', 'Deep Navy'),
    (71, 'double-breasted-wool-blazer', 'DEV-CAT-071', 'Double-Breasted Wool Blazer', 'Blazer len hai hàng khuy', 'ao-khoac', 'APPAREL', 'Charcoal Gray'),
    (72, 'cropped-boucle-jacket', 'DEV-CAT-072', 'Cropped Boucle Jacket', 'Áo khoác bouclé dáng ngắn', 'ao-khoac', 'APPAREL', 'Ivory'),
    (73, 'linen-trench-coat', 'DEV-CAT-073', 'Linen Trench Coat', 'Áo trench linen', 'ao-khoac', 'APPAREL', 'Sand Beige'),
    (74, 'wool-wrap-coat', 'DEV-CAT-074', 'Wool Wrap Coat', 'Áo khoác len đắp chéo', 'ao-khoac', 'APPAREL', 'Olive Green'),
    (75, 'cotton-chore-jacket', 'DEV-CAT-075', 'Cotton Chore Jacket', 'Áo chore cotton', 'ao-khoac', 'APPAREL', 'Deep Navy'),
    (76, 'suede-bomber-jacket', 'DEV-CAT-076', 'Suede Bomber Jacket', 'Áo bomber da lộn', 'ao-khoac', 'APPAREL', 'Terracotta'),
    (77, 'quilted-liner-jacket', 'DEV-CAT-077', 'Quilted Liner Jacket', 'Áo khoác lót chần bông', 'ao-khoac', 'APPAREL', 'Black'),
    (78, 'technical-rain-parka', 'DEV-CAT-078', 'Technical Rain Parka', 'Áo parka kỹ thuật chống mưa', 'ao-khoac', 'APPAREL', 'Olive Green'),
    (79, 'oversized-wool-shacket', 'DEV-CAT-079', 'Oversized Wool Shacket', 'Áo shacket len dáng rộng', 'ao-khoac', 'APPAREL', 'Charcoal Gray'),
    (80, 'belted-safari-jacket', 'DEV-CAT-080', 'Belted Safari Jacket', 'Áo khoác safari thắt đai', 'ao-khoac', 'APPAREL', 'Sand Beige'),
    (81, 'leather-derby', 'DEV-CAT-081', 'Leather Derby', 'Giày Derby da', 'giay', 'SHOES', 'Black'),
    (82, 'suede-oxford', 'DEV-CAT-082', 'Suede Oxford', 'Giày Oxford da lộn', 'giay', 'SHOES', 'Sand Beige'),
    (83, 'chelsea-boot', 'DEV-CAT-083', 'Chelsea Boot', 'Giày boot Chelsea', 'giay', 'SHOES', 'Black'),
    (84, 'ankle-strap-sandal', 'DEV-CAT-084', 'Ankle-Strap Sandal', 'Sandal quai cổ chân', 'giay', 'SHOES', 'Terracotta'),
    (85, 'minimal-leather-mule', 'DEV-CAT-085', 'Minimal Leather Mule', 'Giày mule da tối giản', 'giay', 'SHOES', 'Black'),
    (86, 'ballet-flat', 'DEV-CAT-086', 'Ballet Flat', 'Giày bệt ballet', 'giay', 'SHOES', 'Sand Beige'),
    (87, 'low-heel-slingback', 'DEV-CAT-087', 'Low-Heel Slingback', 'Giày slingback gót thấp', 'giay', 'SHOES', 'Deep Navy'),
    (88, 'hiking-inspired-sneaker', 'DEV-CAT-088', 'Hiking-Inspired Sneaker', 'Giày sneaker phong cách hiking', 'giay', 'SHOES', 'Olive Green'),
    (89, 'canvas-slip-on', 'DEV-CAT-089', 'Canvas Slip-On', 'Giày slip-on canvas', 'giay', 'SHOES', 'White'),
    (90, 'leather-slide', 'DEV-CAT-090', 'Leather Slide', 'Dép slide da', 'giay', 'SHOES', 'Black'),
    (91, 'mini-crossbody-bag', 'DEV-CAT-091', 'Mini Crossbody Bag', 'Túi đeo chéo mini', 'phu-kien', 'ONE_SIZE', 'Terracotta'),
    (92, 'leather-bucket-bag', 'DEV-CAT-092', 'Leather Bucket Bag', 'Túi bucket da', 'phu-kien', 'ONE_SIZE', 'Olive Green'),
    (93, 'structured-satchel', 'DEV-CAT-093', 'Structured Satchel', 'Túi satchel phom cấu trúc', 'phu-kien', 'ONE_SIZE', 'Sand Beige'),
    (94, 'leather-backpack', 'DEV-CAT-094', 'Leather Backpack', 'Balo da', 'phu-kien', 'ONE_SIZE', 'Black'),
    (95, 'belt-bag', 'DEV-CAT-095', 'Belt Bag', 'Túi đeo hông', 'phu-kien', 'ADJUSTABLE', 'Deep Navy'),
    (96, 'woven-market-tote', 'DEV-CAT-096', 'Woven Market Tote', 'Túi tote đan đi chợ', 'phu-kien', 'ONE_SIZE', 'Sand Beige'),
    (97, 'slim-bifold-wallet', 'DEV-CAT-097', 'Slim Bifold Wallet', 'Ví gập đôi mỏng', 'phu-kien', 'ONE_SIZE', 'Black'),
    (98, 'zip-around-wallet', 'DEV-CAT-098', 'Zip-Around Wallet', 'Ví khóa kéo quanh', 'phu-kien', 'ONE_SIZE', 'Olive Green'),
    (99, 'reversible-leather-belt', 'DEV-CAT-099', 'Reversible Leather Belt', 'Thắt lưng da hai mặt', 'phu-kien', 'ADJUSTABLE', 'Dark Brown'),
    (100, 'silk-square-scarf', 'DEV-CAT-100', 'Silk Square Scarf', 'Khăn lụa vuông', 'phu-kien', 'ADJUSTABLE', 'Deep Navy');

DROP TABLE IF EXISTS dev_catalog_enriched_fixture;
CREATE TEMP TABLE dev_catalog_enriched_fixture AS
SELECT
    fixture.*,
    CASE
        WHEN fixture.name_en ILIKE '%cashmere%' THEN 'Pure cashmere'
        WHEN fixture.name_en ILIKE '%suede%' THEN 'Premium suede'
        WHEN fixture.name_en ILIKE '%silk%' THEN 'Mulberry silk'
        WHEN fixture.name_en ILIKE '%linen%' THEN 'European linen'
        WHEN fixture.name_en ILIKE '%wool%' OR fixture.name_en ILIKE '%merino%' THEN 'Wool blend'
        WHEN fixture.name_en ILIKE '%leather%'
          OR fixture.category_slug = 'giay' THEN 'Full-grain leather and rubber'
        WHEN fixture.name_en ILIKE '%cotton%' OR fixture.name_en ILIKE '%poplin%' THEN 'Organic cotton'
        WHEN fixture.name_en ILIKE '%brass%' THEN 'Solid brass'
        WHEN fixture.name_en ILIKE '%chiffon%' OR fixture.name_en ILIKE '%georgette%' THEN 'Lightweight woven blend'
        WHEN fixture.name_en ILIKE '%knit%' OR fixture.name_en ILIKE '%ribbed%' THEN 'Premium knit blend'
        WHEN fixture.category_slug = 'phu-kien' THEN 'Premium accessory materials'
        ELSE 'Natural-fiber blend'
    END::VARCHAR(255) AS material_en,
    CASE
        WHEN fixture.name_en ILIKE '%cashmere%' THEN 'Cashmere nguyên chất'
        WHEN fixture.name_en ILIKE '%suede%' THEN 'Da lộn cao cấp'
        WHEN fixture.name_en ILIKE '%silk%' THEN 'Lụa tơ tằm'
        WHEN fixture.name_en ILIKE '%linen%' THEN 'Linen châu Âu'
        WHEN fixture.name_en ILIKE '%wool%' OR fixture.name_en ILIKE '%merino%' THEN 'Len pha'
        WHEN fixture.name_en ILIKE '%leather%'
          OR fixture.category_slug = 'giay' THEN 'Da nguyên tấm và cao su'
        WHEN fixture.name_en ILIKE '%cotton%' OR fixture.name_en ILIKE '%poplin%' THEN 'Cotton hữu cơ'
        WHEN fixture.name_en ILIKE '%brass%' THEN 'Đồng thau nguyên khối'
        WHEN fixture.name_en ILIKE '%chiffon%' OR fixture.name_en ILIKE '%georgette%' THEN 'Vải dệt nhẹ'
        WHEN fixture.name_en ILIKE '%knit%' OR fixture.name_en ILIKE '%ribbed%' THEN 'Vải dệt kim cao cấp'
        WHEN fixture.category_slug = 'phu-kien' THEN 'Chất liệu phụ kiện cao cấp'
        ELSE 'Sợi tự nhiên pha'
    END::VARCHAR(255) AS material_vi,
    (
        CASE fixture.category_slug
            WHEN 'ao' THEN 390000
            WHEN 'quan' THEN 690000
            WHEN 'vay' THEN 790000
            WHEN 'dam' THEN 1090000
            WHEN 'ao-khoac' THEN 1490000
            WHEN 'giay' THEN 1190000
            WHEN 'phu-kien' THEN 290000
        END + fixture.position * 5000
    )::NUMERIC(15, 2) AS price
FROM dev_catalog_product_fixture fixture;

DROP TABLE IF EXISTS dev_catalog_color_fixture;
CREATE TEMP TABLE dev_catalog_color_fixture (
    product_position INTEGER NOT NULL,
    color_name VARCHAR(80) NOT NULL,
    image_paths TEXT[] NOT NULL,
    PRIMARY KEY (product_position, color_name),
    CONSTRAINT fk_dev_catalog_color_product
        FOREIGN KEY (product_position)
        REFERENCES dev_catalog_product_fixture(position)
        ON DELETE CASCADE,
    CONSTRAINT ck_dev_catalog_color_images CHECK (cardinality(image_paths) >= 3)
);

INSERT INTO dev_catalog_color_fixture (product_position, color_name, image_paths)
VALUES
    (1, 'White', ARRAY['artisanal_fashion_product_shot_for_vela_wear._a_premium_garment_in_crisp_white.png', 'seed_001_crisp-white-linen-blazer_white_back.png', 'full_product_detail_gallery_set_for_a_single_artisanal_crisp_white_ffffff_linen.png']::TEXT[]),
    (2, 'Black', ARRAY['essential_cotton_tee_black.png', 'essential_cotton_tee_black_back.png', 'essential_cotton_tee_black_detail.png']::TEXT[]),
    (2, 'Red', ARRAY['essential_cotton_tee_red.png', 'essential_cotton_tee_red_back.png', 'essential_cotton_tee_red_detail.png']::TEXT[]),
    (3, 'Charcoal Gray', ARRAY['high_resolution_individual_product_shot_of_a_charcoal_gray_808080_cashmere.png', 'individual_product_shot_of_the_back_view_of_a_charcoal_gray_808080_cashmere.png', 'high_end_minimalist_editorial_for_vela_wear._a_male_model_in_a_charcoal_gray.png', 'side_profile_shot_of_a_male_model_wearing_a_charcoal_gray_808080_cashmere.png', 'detail_shot_of_the_ribbed_hem_and_stitching_on_a_charcoal_gray_808080_cashmere.png', 'extreme_macro_close_up_shot_of_the_charcoal_gray_808080_soft_cashmere_knit.png', 'full_product_detail_gallery_set_for_a_single_charcoal_gray_808080_cashmere.png']::TEXT[]),
    (3, 'Olive Green', ARRAY['individual_product_shot_of_a_luxury_olive_green_556b2f_cashmere_sweater_front.png', 'full_product_detail_gallery_set_for_a_single_luxury_olive_green_556b2f_cashmere.png', 'full_product_detail_gallery_set_for_a_single_olive_green_556b2f_cashmere.png', 'macro_shot_of_the_olive_green_556b2f_soft_cashmere_knit_weave._high_end.png', 'product_detail_gallery_set_for_an_olive_green_556b2f_cashmere_sweater._4.png']::TEXT[]),
    (4, 'White', ARRAY['high_end_fashion_editorial_for_vela_wear._a_male_model_wearing_crisp_white.png', 'full_product_detail_gallery_set_for_a_single_crisp_white_ffffff_linen_shirt_and.png', 'seed_004_crisp-white-linen-shirt_white_detail.png']::TEXT[]),
    (5, 'Terracotta', ARRAY['editorial_lifestyle_photography_for_vela_wear._a_female_model_in_minimalist.png', 'seed_005_terracotta-knit-lounge-set_terracotta_back.png', 'seed_005_terracotta-knit-lounge-set_terracotta_detail.png']::TEXT[]),
    (6, 'Deep Navy', ARRAY['editorial_fashion_photography_for_vela_wear._a_premium_tailored_garment_in_deep.png', 'high_end_fashion_editorial_shot_for_vela_wear._a_model_wearing_a_premium.png', 'full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_suit_2.png']::TEXT[]),
    (7, 'Charcoal Gray', ARRAY['high_end_fashion_editorial_shot_for_vela_wear._a_model_wearing_a_structured.png', 'seed_007_womens-structured-pantsuit_charcoal-gray_back.png', 'full_product_detail_gallery_set_for_a_single_structured_charcoal_gray_808080.png']::TEXT[]),
    (8, 'Terracotta', ARRAY['individual_product_shot_of_a_premium_terracotta_b5573a_linen_jumpsuit_front.png', 'editorial_lifestyle_photography_for_vela_wear._a_model_wearing_minimalist.png', 'full_product_detail_gallery_set_for_a_single_minimalist_terracotta_b5573a_linen.png', 'full_product_detail_gallery_set_for_a_single_premium_terracotta_b5573a_linen.png', 'macro_shot_of_the_terracotta_b5573a_linen_fabric_texture_and_v_neckline_on_a.png']::TEXT[]),
    (9, 'Deep Navy', ARRAY['high_resolution_individual_product_shot_of_a_structured_deep_navy_1f3a5f_wool.png', 'individual_product_shot_of_the_back_view_of_a_structured_deep_navy_1f3a5f_wool.png', 'full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_wool.png', 'macro_detail_shot_of_the_waist_belt_and_stitching_on_a_structured_deep_navy.png', 'product_detail_gallery_set_for_a_structured_deep_navy_1f3a5f_wool_jumpsuit._4.png']::TEXT[]),
    (10, 'Deep Navy', ARRAY['editorial_fashion_shot_for_vela_wear._a_female_model_wearing_a_structured_deep.png', 'seed_010_navy-wrap-tailored-jumpsuit_deep-navy_back.png', 'seed_010_navy-wrap-tailored-jumpsuit_deep-navy_detail.png']::TEXT[]),
    (11, 'Charcoal Gray', ARRAY['high_resolution_individual_product_shot_of_a_charcoal_gray_808080_silk_midi.png', 'individual_product_shot_of_the_back_view_of_a_charcoal_gray_808080_silk_midi.png', 'high_end_fashion_editorial_photography_for_vela_wear._a_female_model_wearing_a.png', 'detail_shot_of_the_hemline_and_flowing_silhouette_of_a_charcoal_gray_808080.png', 'full_product_detail_gallery_set_for_a_single_charcoal_gray_808080_silk_midi.png', 'macro_close_up_shot_of_charcoal_gray_808080_silk_fabric_texture_showing_the.png', 'product_detail_gallery_set_for_a_charcoal_gray_808080_silk_midi_dress._4.png']::TEXT[]),
    (12, 'Sand Beige', ARRAY['professional_studio_photography_of_an_artisanal_garment_in_warm_sand_beige.png', 'seed_012_artisan-linen-dress_sand-beige_back.png', 'full_product_detail_gallery_set_for_a_single_sand_beige_d8cab8_artisanal_dress.png']::TEXT[]),
    (12, 'Yellow', ARRAY['urban_linen_dress_yellow.png', 'urban_linen_dress_yellow_studio.png', 'urban_linen_dress_yellow_back.png']::TEXT[]),
    (13, 'White', ARRAY['sneaker_af_1.png', 'sneaker_af_2.png', 'sneaker_af_3.png', 'sneaker_af_4.png']::TEXT[]),
    (14, 'Black', ARRAY['individual_product_shot_of_a_luxury_minimalist_black_000000_leather_loafer_side.png', 'professional_studio_product_photography_for_vela_wear._minimalist_leather.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_black_000000.png']::TEXT[]),
    (15, 'Sand Beige', ARRAY['individual_product_shot_of_a_luxury_minimalist_sand_beige_d8cab8_linen_blazer.png', 'individual_product_shot_of_the_back_view_of_a_luxury_minimalist_sand_beige.png', 'professional_studio_photography_for_vela_wear._a_high_quality_tailored_piece_in.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_sand_beige_1.png', 'macro_close_up_shot_of_the_sand_beige_d8cab8_linen_fabric_texture_on_a_blazer.png', 'product_detail_gallery_set_for_a_luxury_minimalist_sand_beige_d8cab8_linen.png']::TEXT[]),
    (16, 'Olive Green', ARRAY['premium_fashion_product_shot_of_a_high_quality_tailored_piece_in_olive_green.png', 'seed_016_north-structured-blazer_olive-green_back.png', 'full_product_detail_gallery_set_for_a_single_luxury_olive_green_556b2f_tailored.png']::TEXT[]),
    (16, 'Purple', ARRAY['north_structured_blazer_purple_front.png', 'north_structured_blazer_purple.png', 'north_structured_blazer_purple_details.png']::TEXT[]),
    (17, 'Black', ARRAY['seed_017_black-hooded-linen-cape_black_front.png', 'high_end_minimalist_fashion_editorial_photography_of_a_premium_garment_in_deep.png', 'full_product_detail_gallery_set_for_a_single_premium_black_000000_hooded_linen.png']::TEXT[]),
    (18, 'Deep Navy', ARRAY['seed_018_navy-pleated-cape-trouser-set_deep-navy_front.png', 'lifestyle_fashion_photography_of_a_model_wearing_sophisticated_apparel_in_deep.png', 'full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f.png']::TEXT[]),
    (19, 'Deep Navy', ARRAY['individual_product_shot_of_a_male_model_wearing_a_structured_deep_navy_1f3a5f.png', 'individual_product_shot_of_a_structured_deep_navy_1f3a5f_linen_wool_blazer_on_a.png', 'high_end_fashion_editorial_photography_for_vela_wear._a_male_model_wearing_a.png', 'full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_blazer.png', 'macro_shot_of_the_deep_navy_1f3a5f_linen_wool_blend_fabric_texture_on_a_blazer_.png']::TEXT[]),
    (20, 'Deep Navy', ARRAY['seed_020_mens-navy-tailored-suit_deep-navy_back.png', 'full_product_detail_gallery_set_for_a_single_structured_deep_navy_1f3a5f_suit_1.png', 'seed_020_mens-navy-tailored-suit_deep-navy_detail.png']::TEXT[]),
    (21, 'Olive Green', ARRAY['high_end_minimalist_fashion_editorial_for_vela_wear._a_male_model_in_a_tailored.png', 'seed_021_olive-wool-overcoat_olive-green_back.png', 'seed_021_olive-wool-overcoat_olive-green_detail.png']::TEXT[]),
    (22, 'Black', ARRAY['tailored_black_trousers.png', 'tailored_black_trousers_back.png', 'tailored_black_trousers_detail.png']::TEXT[]),
    (23, 'Black', ARRAY['individual_product_shot_of_a_premium_black_000000_leather_tote_bag_perspective.png', 'full_product_detail_gallery_set_for_a_single_premium_black_000000_leather_tote.png', 'macro_close_up_shot_of_the_black_000000_pebbled_leather_texture_and_embossed.png', 'product_detail_gallery_set_for_a_premium_black_000000_leather_tote_bag._4.png']::TEXT[]),
    (23, 'Orange', ARRAY['studio_leather_tote_orange.png', 'studio_leather_tote_orange_detail.png', 'studio_leather_tote_orange_gallery.png']::TEXT[]),
    (24, 'Sand Beige', ARRAY['luxury_accessory_product_shot_for_vela_wear._a_minimalist_bag_in_sand_beige.png', 'professional_studio_product_photography_for_vela_wear._a_minimalist_leather.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_sand_beige_2.png']::TEXT[]),
    (25, 'Black', ARRAY['minimalist_editorial_photography_for_vela_wear._a_sophisticated_fashion.png', 'seed_025_black-crescent-shoulder-bag_black_back.png', 'full_product_detail_gallery_set_for_a_single_premium_black_000000_leather_bag.png']::TEXT[]),
    (26, 'Charcoal Gray', ARRAY['minimalist_editorial_photography_of_a_premium_accessory_in_refined_charcoal.png', 'full_product_detail_gallery_set_for_a_single_premium_charcoal_gray_808080.png', 'seed_026_charcoal-top-handle-bag_charcoal-gray_detail.png']::TEXT[]),
    (27, 'Black', ARRAY['professional_studio_photography_for_vela_wear._a_minimalist_accessory_in_deep.png', 'seed_027_black-leather-clutch_black_back.png', 'full_product_detail_gallery_set_for_a_single_minimalist_black_000000_leather.png']::TEXT[]),
    (28, 'Sand Beige', ARRAY['seed_028_sand-leather-wallet_sand-beige_back.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_sand_beige_3.png', 'seed_028_sand-leather-wallet_sand-beige_detail.png']::TEXT[]),
    (29, 'Olive Green', ARRAY['professional_studio_product_photography_for_vela_wear._a_luxury_minimalist.png', 'seed_029_olive-zip-pouch-cardholder-set_olive-green_back.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_olive_green.png']::TEXT[]),
    (30, 'White', ARRAY['professional_studio_product_photography_of_a_luxury_minimalist_accessory_in.png', 'full_product_detail_gallery_set_for_a_single_luxury_minimalist_crisp_white.png', 'seed_030_white-sculptural-cuff_white_detail.png']::TEXT[]),
    (31, 'Brass', ARRAY['key_hook_1.png', 'seed_031_brass-ring-key-hook_brass_back.png', 'seed_031_brass-ring-key-hook_brass_detail.png']::TEXT[]),
    (32, 'Brass', ARRAY['key_hook_2.png', 'seed_032_brass-double-wall-hook_brass_back.png', 'seed_032_brass-double-wall-hook_brass_detail.png']::TEXT[]),
    (33, 'Brown', ARRAY['key_hook_3.png', 'seed_033_leather-loop-key-hook_brown_back.png', 'seed_033_leather-loop-key-hook_brown_detail.png']::TEXT[]),
    (34, 'Brass', ARRAY['key_hook_4.png', 'seed_034_brass-key-rack_brass_back.png', 'seed_034_brass-key-rack_brass_detail.png']::TEXT[]),
    (35, 'Deep Navy', ARRAY['seed_35_pima-cotton-henley_front.png', 'seed_35_pima-cotton-henley_back.png', 'seed_35_pima-cotton-henley_detail.png']::TEXT[]),
    (36, 'Charcoal Gray', ARRAY['seed_36_ribbed-merino-turtleneck_front.png', 'seed_36_ribbed-merino-turtleneck_back.png', 'seed_36_ribbed-merino-turtleneck_detail.png']::TEXT[]),
    (37, 'Terracotta', ARRAY['seed_37_silk-camp-collar-shirt_front.png', 'seed_37_silk-camp-collar-shirt_back.png', 'seed_37_silk-camp-collar-shirt_detail.png']::TEXT[]),
    (38, 'White', ARRAY['seed_38_linen-grandad-collar-shirt_front.png', 'seed_38_linen-grandad-collar-shirt_back.png', 'seed_38_linen-grandad-collar-shirt_detail.png']::TEXT[]),
    (39, 'Olive Green', ARRAY['seed_39_organic-cotton-polo_front.png', 'seed_39_organic-cotton-polo_back.png', 'seed_39_organic-cotton-polo_detail.png']::TEXT[]),
    (40, 'Sand Beige', ARRAY['seed_40_draped-silk-blouse_front.png', 'seed_40_draped-silk-blouse_back.png', 'seed_40_draped-silk-blouse_detail.png']::TEXT[]),
    (41, 'Ivory', ARRAY['seed_41_pleated-georgette-blouse_front.png', 'seed_41_pleated-georgette-blouse_back.png', 'seed_41_pleated-georgette-blouse_detail.png']::TEXT[]),
    (42, 'White', ARRAY['seed_42_cropped-boxy-linen-shirt_front.png', 'seed_42_cropped-boxy-linen-shirt_back.png', 'seed_42_cropped-boxy-linen-shirt_detail.png']::TEXT[]),
    (43, 'Charcoal Gray', ARRAY['seed_43_wool-silk-mockneck_front.png', 'seed_43_wool-silk-mockneck_back.png', 'seed_43_wool-silk-mockneck_detail.png']::TEXT[]),
    (44, 'Sand Beige', ARRAY['seed_44_ribbed-cashmere-cardigan_front.png', 'seed_44_ribbed-cashmere-cardigan_back.png', 'seed_44_ribbed-cashmere-cardigan_detail.png']::TEXT[]),
    (45, 'Olive Green', ARRAY['seed_45_linen-overshirt_front.png', 'seed_45_linen-overshirt_back.png', 'seed_45_linen-overshirt_detail.png']::TEXT[]),
    (46, 'White', ARRAY['seed_46_relaxed-poplin-tunic_front.png', 'seed_46_relaxed-poplin-tunic_back.png', 'seed_46_relaxed-poplin-tunic_detail.png']::TEXT[]),
    (47, 'Charcoal Gray', ARRAY['seed_47_pleated-wool-trousers_front.png', 'seed_47_pleated-wool-trousers_back.png', 'seed_47_pleated-wool-trousers_detail.png']::TEXT[]),
    (48, 'Sand Beige', ARRAY['seed_48_wide-leg-linen-trousers_front.png', 'seed_48_wide-leg-linen-trousers_back.png', 'seed_48_wide-leg-linen-trousers_detail.png']::TEXT[]),
    (49, 'Olive Green', ARRAY['seed_49_tapered-cotton-chinos_front.png', 'seed_49_tapered-cotton-chinos_back.png', 'seed_49_tapered-cotton-chinos_detail.png']::TEXT[]),
    (50, 'Deep Navy', ARRAY['seed_50_silk-drawstring-trousers_front.png', 'seed_50_silk-drawstring-trousers_back.png', 'seed_50_silk-drawstring-trousers_detail.png']::TEXT[]),
    (51, 'Black', ARRAY['seed_51_cropped-cigarette-trousers_front.png', 'seed_51_cropped-cigarette-trousers_back.png', 'seed_51_cropped-cigarette-trousers_detail.png']::TEXT[]),
    (52, 'Terracotta', ARRAY['seed_52_utility-cargo-trousers_front.png', 'seed_52_utility-cargo-trousers_back.png', 'seed_52_utility-cargo-trousers_detail.png']::TEXT[]),
    (53, 'Charcoal Gray', ARRAY['seed_53_bias-cut-silk-midi-skirt_front.png', 'seed_53_bias-cut-silk-midi-skirt_back.png', 'seed_53_bias-cut-silk-midi-skirt_detail.png']::TEXT[]),
    (54, 'Deep Navy', ARRAY['seed_54_pleated-wool-midi-skirt_front.png', 'seed_54_pleated-wool-midi-skirt_back.png', 'seed_54_pleated-wool-midi-skirt_detail.png']::TEXT[]),
    (55, 'Sand Beige', ARRAY['seed_55_linen-wrap-skirt_front.png', 'seed_55_linen-wrap-skirt_back.png', 'seed_55_linen-wrap-skirt_detail.png']::TEXT[]),
    (56, 'White', ARRAY['seed_56_cotton-a-line-skirt_front.png', 'seed_56_cotton-a-line-skirt_back.png', 'seed_56_cotton-a-line-skirt_detail.png']::TEXT[]),
    (57, 'Black', ARRAY['seed_57_leather-pencil-skirt_front.png', 'seed_57_leather-pencil-skirt_back.png', 'seed_57_leather-pencil-skirt_detail.png']::TEXT[]),
    (58, 'Olive Green', ARRAY['seed_58_draped-maxi-skirt_front.png', 'seed_58_draped-maxi-skirt_back.png', 'seed_58_draped-maxi-skirt_detail.png']::TEXT[]),
    (59, 'Black', ARRAY['seed_59_silk-slip-dress_front.png', 'seed_59_silk-slip-dress_back.png', 'seed_59_silk-slip-dress_detail.png']::TEXT[]),
    (60, 'Sand Beige', ARRAY['seed_60_linen-shirt-dress_front.png', 'seed_60_linen-shirt-dress_back.png', 'seed_60_linen-shirt-dress_detail.png']::TEXT[]),
    (61, 'Charcoal Gray', ARRAY['seed_61_wool-column-dress_front.png', 'seed_61_wool-column-dress_back.png', 'seed_61_wool-column-dress_detail.png']::TEXT[]),
    (62, 'White', ARRAY['seed_62_cotton-poplin-midi-dress_front.png', 'seed_62_cotton-poplin-midi-dress_back.png', 'seed_62_cotton-poplin-midi-dress_detail.png']::TEXT[]),
    (63, 'Terracotta', ARRAY['seed_63_wrap-jersey-dress_front.png', 'seed_63_wrap-jersey-dress_back.png', 'seed_63_wrap-jersey-dress_detail.png']::TEXT[]),
    (64, 'Deep Navy', ARRAY['seed_64_pleated-chiffon-maxi-dress_front.png', 'seed_64_pleated-chiffon-maxi-dress_back.png', 'seed_64_pleated-chiffon-maxi-dress_detail.png']::TEXT[]),
    (65, 'Olive Green', ARRAY['seed_65_knit-rib-midi-dress_front.png', 'seed_65_knit-rib-midi-dress_back.png', 'seed_65_knit-rib-midi-dress_detail.png']::TEXT[]),
    (66, 'Sand Beige', ARRAY['seed_66_belted-safari-dress_front.png', 'seed_66_belted-safari-dress_back.png', 'seed_66_belted-safari-dress_detail.png']::TEXT[]),
    (67, 'Black', ARRAY['seed_67_one-shoulder-evening-dress_front.png', 'seed_67_one-shoulder-evening-dress_back.png', 'seed_67_one-shoulder-evening-dress_detail.png']::TEXT[]),
    (68, 'White', ARRAY['seed_68_linen-apron-dress_front.png', 'seed_68_linen-apron-dress_back.png', 'seed_68_linen-apron-dress_detail.png']::TEXT[]),
    (69, 'Olive Green', ARRAY['seed_69_utility-cotton-jumpsuit_front.png', 'seed_69_utility-cotton-jumpsuit_back.png', 'seed_69_utility-cotton-jumpsuit_detail.png']::TEXT[]),
    (70, 'Deep Navy', ARRAY['seed_70_sleeveless-tailored-jumpsuit_front.png', 'seed_70_sleeveless-tailored-jumpsuit_back.png', 'seed_70_sleeveless-tailored-jumpsuit_detail.png']::TEXT[]),
    (71, 'Charcoal Gray', ARRAY['seed_71_double-breasted-wool-blazer_front.png', 'seed_71_double-breasted-wool-blazer_back.png', 'seed_71_double-breasted-wool-blazer_detail.png']::TEXT[]),
    (72, 'Ivory', ARRAY['seed_72_cropped-boucle-jacket_front.png', 'seed_72_cropped-boucle-jacket_back.png', 'seed_72_cropped-boucle-jacket_detail.png']::TEXT[]),
    (73, 'Sand Beige', ARRAY['seed_73_linen-trench-coat_front.png', 'seed_73_linen-trench-coat_back.png', 'seed_73_linen-trench-coat_detail.png']::TEXT[]),
    (74, 'Olive Green', ARRAY['seed_74_wool-wrap-coat_front.png', 'seed_74_wool-wrap-coat_back.png', 'seed_74_wool-wrap-coat_detail.png']::TEXT[]),
    (75, 'Deep Navy', ARRAY['seed_75_cotton-chore-jacket_front.png', 'seed_75_cotton-chore-jacket_back.png', 'seed_75_cotton-chore-jacket_detail.png']::TEXT[]),
    (76, 'Terracotta', ARRAY['seed_76_suede-bomber-jacket_front.png', 'seed_76_suede-bomber-jacket_back.png', 'seed_76_suede-bomber-jacket_detail.png']::TEXT[]),
    (77, 'Black', ARRAY['seed_77_quilted-liner-jacket_front.png', 'seed_77_quilted-liner-jacket_back.png', 'seed_77_quilted-liner-jacket_detail.png']::TEXT[]),
    (78, 'Olive Green', ARRAY['seed_78_technical-rain-parka_front.png', 'seed_78_technical-rain-parka_back.png', 'seed_78_technical-rain-parka_detail.png']::TEXT[]),
    (79, 'Charcoal Gray', ARRAY['seed_79_oversized-wool-shacket_front.png', 'seed_79_oversized-wool-shacket_back.png', 'seed_79_oversized-wool-shacket_detail.png']::TEXT[]),
    (80, 'Sand Beige', ARRAY['seed_80_belted-safari-jacket_front.png', 'seed_80_belted-safari-jacket_back.png', 'seed_80_belted-safari-jacket_detail.png']::TEXT[]),
    (81, 'Black', ARRAY['seed_81_leather-derby_front.png', 'seed_81_leather-derby_back.png', 'seed_81_leather-derby_detail.png']::TEXT[]),
    (82, 'Sand Beige', ARRAY['seed_82_suede-oxford_front.png', 'seed_82_suede-oxford_back.png', 'seed_82_suede-oxford_detail.png']::TEXT[]),
    (83, 'Black', ARRAY['seed_83_chelsea-boot_front.png', 'seed_83_chelsea-boot_back.png', 'seed_83_chelsea-boot_detail.png']::TEXT[]),
    (84, 'Terracotta', ARRAY['seed_84_ankle-strap-sandal_front.png', 'seed_84_ankle-strap-sandal_back.png', 'seed_84_ankle-strap-sandal_detail.png']::TEXT[]),
    (85, 'Black', ARRAY['seed_85_minimal-leather-mule_front.png', 'seed_85_minimal-leather-mule_back.png', 'seed_85_minimal-leather-mule_detail.png']::TEXT[]),
    (86, 'Sand Beige', ARRAY['seed_86_ballet-flat_front.png', 'seed_86_ballet-flat_back.png', 'seed_86_ballet-flat_detail.png']::TEXT[]),
    (87, 'Deep Navy', ARRAY['seed_87_low-heel-slingback_front.png', 'seed_87_low-heel-slingback_back.png', 'seed_87_low-heel-slingback_detail.png']::TEXT[]),
    (88, 'Olive Green', ARRAY['seed_88_hiking-inspired-sneaker_front.png', 'seed_88_hiking-inspired-sneaker_back.png', 'seed_88_hiking-inspired-sneaker_detail.png']::TEXT[]),
    (89, 'White', ARRAY['seed_89_canvas-slip-on_front.png', 'seed_89_canvas-slip-on_back.png', 'seed_89_canvas-slip-on_detail.png']::TEXT[]),
    (90, 'Black', ARRAY['seed_90_leather-slide_front.png', 'seed_90_leather-slide_back.png', 'seed_90_leather-slide_detail.png']::TEXT[]),
    (91, 'Terracotta', ARRAY['seed_91_mini-crossbody-bag_front.png', 'seed_91_mini-crossbody-bag_back.png', 'seed_91_mini-crossbody-bag_detail.png']::TEXT[]),
    (92, 'Olive Green', ARRAY['seed_92_leather-bucket-bag_front.png', 'seed_92_leather-bucket-bag_back.png', 'seed_92_leather-bucket-bag_detail.png']::TEXT[]),
    (93, 'Sand Beige', ARRAY['seed_93_structured-satchel_front.png', 'seed_93_structured-satchel_back.png', 'seed_93_structured-satchel_detail.png']::TEXT[]),
    (94, 'Black', ARRAY['seed_94_leather-backpack_front.png', 'seed_94_leather-backpack_back.png', 'seed_94_leather-backpack_detail.png']::TEXT[]),
    (95, 'Deep Navy', ARRAY['seed_95_belt-bag_front.png', 'seed_95_belt-bag_back.png', 'seed_95_belt-bag_detail.png']::TEXT[]),
    (96, 'Sand Beige', ARRAY['seed_96_woven-market-tote_front.png', 'seed_96_woven-market-tote_back.png', 'seed_96_woven-market-tote_detail.png']::TEXT[]),
    (97, 'Black', ARRAY['seed_97_slim-bifold-wallet_front.png', 'seed_97_slim-bifold-wallet_back.png', 'seed_97_slim-bifold-wallet_detail.png']::TEXT[]),
    (98, 'Olive Green', ARRAY['seed_98_zip-around-wallet_front.png', 'seed_98_zip-around-wallet_back.png', 'seed_98_zip-around-wallet_detail.png']::TEXT[]),
    (99, 'Dark Brown', ARRAY['seed_99_reversible-leather-belt_front.png', 'seed_99_reversible-leather-belt_back.png', 'seed_99_reversible-leather-belt_detail.png']::TEXT[]),
    (100, 'Deep Navy', ARRAY['seed_100_silk-square-scarf_front.png', 'seed_100_silk-square-scarf_back.png', 'seed_100_silk-square-scarf_detail.png']::TEXT[]);

DO $$
BEGIN
    IF (SELECT count(*) FROM dev_catalog_product_fixture) <> 100 THEN
        RAISE EXCEPTION 'Dev catalog must contain exactly 100 products';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM dev_catalog_product_fixture fixture
        LEFT JOIN categories category ON category.slug = fixture.category_slug
        WHERE category.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Dev catalog contains an unknown category';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM brands WHERE slug = 'velawear') THEN
        RAISE EXCEPTION 'Dev catalog requires brand velawear';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM dev_catalog_color_fixture fixture
        LEFT JOIN colors color ON color.name = fixture.color_name
        WHERE color.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Dev catalog contains an unknown color';
    END IF;

    IF EXISTS (
        SELECT image_path
        FROM dev_catalog_color_fixture fixture
        CROSS JOIN LATERAL unnest(fixture.image_paths) image_path
        GROUP BY image_path
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'A product image is assigned to more than one product-color';
    END IF;

    IF (
        SELECT count(*)
        FROM dev_catalog_color_fixture fixture
        CROSS JOIN LATERAL unnest(fixture.image_paths) image_path
    ) <> 336 THEN
        RAISE EXCEPTION 'Dev catalog must map all 336 product PNG assets';
    END IF;
END $$;

-- Capture every row ever owned by this seed before canonical upserts.
DROP TABLE IF EXISTS dev_catalog_known_product;
CREATE TEMP TABLE dev_catalog_known_product (
    product_id BIGINT PRIMARY KEY
);

WITH known_alias(slug) AS (
    VALUES
        ('essential-cotton-tee'),
        ('urban-linen-dress'),
        ('north-utility-jacket'),
        ('studio-canvas-tote'),
        ('tailored-black-trousers'),
        ('minimal-white-leather-sneakers'),
        ('charcoal-cashmere-crewneck'),
        ('olive-cashmere-crewneck'),
        ('cashmere-crewneck'),
        ('charcoal-silk-midi-dress'),
        ('terracotta-linen-jumpsuit'),
        ('deep-navy-wool-jumpsuit'),
        ('deep-navy-womens-tailored-suit'),
        ('deep-navy-linen-wool-blazer'),
        ('sand-beige-linen-blazer'),
        ('black-leather-loafers'),
        ('vela-brass-key-hook'),
        ('sand-beige-leather-tote'),
        ('black-leather-tote'),
        ('crisp-white-linen-shirt'),
        ('artisanal-white-linen-blazer'),
        ('crisp-white-linen-blazer'),
        ('black-hooded-linen-cape'),
        ('sand-beige-artisan-dress'),
        ('olive-tailored-blazer'),
        ('olive-wool-overcoat'),
        ('charcoal-structured-pantsuit'),
        ('black-crescent-leather-bag'),
        ('black-leather-clutch'),
        ('charcoal-structured-handbag'),
        ('olive-leather-cardholder'),
        ('sand-beige-leather-wallet'),
        ('white-sculptural-leather-cuff')
)
INSERT INTO dev_catalog_known_product (product_id)
SELECT product.id
FROM products product
WHERE EXISTS (
        SELECT 1
        FROM product_attributes owner
        WHERE owner.product_id = product.id
          AND owner.name = 'SeedOwner'
          AND owner.value = 'R3_PRODUCT_CATALOG_100'
    )
   OR product.slug IN (SELECT slug FROM dev_catalog_product_fixture)
   OR product.slug IN (SELECT slug FROM known_alias)
   OR (
       product.slug = lower(replace(product.name, ' ', '-'))
       AND product.description = 'Premium ' || product.name
           || ' with fine stitching and sustainable design.'
   )
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO products (
    name, slug, description, category_id, brand_id, status, deleted_at
)
SELECT
    fixture.name_en,
    fixture.slug,
    fixture.name_en || ' made from ' || lower(fixture.material_en)
        || ' with dedicated color-matched product photography.',
    category.id,
    brand.id,
    'ACTIVE',
    NULL
FROM dev_catalog_enriched_fixture fixture
JOIN categories category ON category.slug = fixture.category_slug
JOIN brands brand ON brand.slug = 'velawear'
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    status = EXCLUDED.status,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO dev_catalog_known_product (product_id)
SELECT product.id
FROM products product
JOIN dev_catalog_product_fixture fixture ON fixture.slug = product.slug
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO product_attributes (product_id, name, value)
SELECT known.product_id, 'SeedOwner', 'R3_PRODUCT_CATALOG_100'
FROM dev_catalog_known_product known
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

-- Free every localized slug owned by this seed first. This makes swaps and
-- upgrades idempotent under UNIQUE(locale_code, slug).
UPDATE product_translations translation
SET slug = 'dev-archived-' || translation.product_id || '-' || translation.locale_code,
    updated_at = CURRENT_TIMESTAMP
FROM dev_catalog_known_product known
WHERE known.product_id = translation.product_id;

WITH localized AS (
    SELECT
        fixture.slug,
        'en'::VARCHAR AS locale_code,
        fixture.name_en AS localized_name,
        fixture.slug AS localized_slug,
        'A deterministic storefront fixture with a color-matched gallery.' AS short_description,
        fixture.name_en || ' is made from ' || lower(fixture.material_en)
            || ' and uses only photographs assigned to the same product and color.' AS description,
        fixture.material_en AS material,
        CASE
            WHEN fixture.category_slug IN ('ao', 'quan', 'vay', 'dam', 'ao-khoac')
                THEN 'Follow the care label; use gentle washing or dry cleaning as appropriate.'
            WHEN fixture.category_slug = 'giay'
                THEN 'Wipe clean with a soft cloth and keep dry.'
            ELSE 'Wipe gently and store in a dry place.'
        END AS care_instruction
    FROM dev_catalog_enriched_fixture fixture

    UNION ALL

    SELECT
        fixture.slug,
        'vi'::VARCHAR,
        fixture.name_vi,
        CASE fixture.slug
            WHEN 'essential-cotton-tee' THEN 'ao-thun-cotton-thiet-yeu'
            WHEN 'urban-linen-dress' THEN 'dam-linen-do-thi'
            WHEN 'north-utility-jacket' THEN 'ao-blazer-north-may-cau-truc'
            WHEN 'studio-canvas-tote' THEN 'tui-tote-da-studio'
            WHEN 'tailored-black-trousers' THEN 'quan-tay-den-may-do'
            WHEN 'minimal-white-leather-sneakers' THEN 'giay-sneaker-da-trang-toi-gian'
            ELSE fixture.slug
        END,
        'Sản phẩm seed xác định với bộ ảnh khớp đúng màu.' AS short_description,
        fixture.name_vi || ' làm từ ' || lower(fixture.material_vi)
            || ', chỉ sử dụng ảnh thuộc đúng sản phẩm và màu tương ứng.' AS description,
        fixture.material_vi,
        CASE
            WHEN fixture.category_slug IN ('ao', 'quan', 'vay', 'dam', 'ao-khoac')
                THEN 'Tuân theo nhãn hướng dẫn; giặt nhẹ hoặc giặt khô tùy chất liệu.'
            WHEN fixture.category_slug = 'giay'
                THEN 'Lau bằng khăn mềm và giữ sản phẩm khô ráo.'
            ELSE 'Lau nhẹ và bảo quản nơi khô ráo.'
        END
    FROM dev_catalog_enriched_fixture fixture
)
INSERT INTO product_translations (
    product_id, locale_code, name, slug, short_description, description,
    material, care_instruction, seo_title, seo_description
)
SELECT
    product.id,
    localized.locale_code,
    localized.localized_name,
    localized.localized_slug,
    localized.short_description,
    localized.description,
    localized.material,
    localized.care_instruction,
    localized.localized_name,
    localized.short_description
FROM localized
JOIN products product ON product.slug = localized.slug
ON CONFLICT (product_id, locale_code) DO UPDATE
SET name = EXCLUDED.name,
    slug = EXCLUDED.slug,
    short_description = EXCLUDED.short_description,
    description = EXCLUDED.description,
    material = EXCLUDED.material,
    care_instruction = EXCLUDED.care_instruction,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description,
    updated_at = CURRENT_TIMESTAMP;

-- Rows formerly produced by the random/experimental seed remain available to
-- FK history, but soft-delete keeps them out of the Collection endpoint.
UPDATE products product
SET status = 'INACTIVE',
    deleted_at = COALESCE(product.deleted_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
FROM dev_catalog_known_product known
WHERE known.product_id = product.id
  AND NOT EXISTS (
      SELECT 1
      FROM dev_catalog_product_fixture fixture
      WHERE fixture.slug = product.slug
  );

DROP TABLE IF EXISTS dev_catalog_expected_variant;
CREATE TEMP TABLE dev_catalog_expected_variant AS
SELECT
    fixture.position AS product_position,
    product.id AS product_id,
    color.id AS color_id,
    size.id AS size_id,
    expanded_size.size_position,
    CASE
        WHEN fixture.slug = 'essential-cotton-tee'
          AND color.name = 'Black' AND expanded_size.size_name = 'M'
            THEN 'VW-TEE-BLK-M'
        WHEN fixture.slug = 'essential-cotton-tee'
          AND color.name = 'Red' AND expanded_size.size_name = 'L'
            THEN 'VW-TEE-RED-L'
        WHEN fixture.slug = 'urban-linen-dress'
          AND color.name = 'Yellow' AND expanded_size.size_name = 'S'
            THEN 'UT-DRESS-YLW-S'
        WHEN fixture.slug = 'north-utility-jacket'
          AND color.name = 'Purple' AND expanded_size.size_name = 'M'
            THEN 'NS-JACKET-PUR-M'
        WHEN fixture.slug = 'studio-canvas-tote'
          AND color.name = 'Orange' AND expanded_size.size_name = 'ONE SIZE'
            THEN 'SV-TOTE-ORG-OS'
        WHEN fixture.slug = 'tailored-black-trousers'
          AND color.name = 'Black' AND expanded_size.size_name = 'M'
            THEN 'VW-TRS-BLK-M'
        WHEN fixture.slug = 'minimal-white-leather-sneakers'
          AND color.name = 'White' AND expanded_size.size_name = '39'
            THEN 'VW-SNK-WHT-39'
        ELSE fixture.sku_prefix || '-'
            || regexp_replace(upper(color.name), '[^A-Z0-9]+', '-', 'g') || '-'
            || regexp_replace(upper(expanded_size.size_name), '[^A-Z0-9]+', '-', 'g')
    END::VARCHAR(100) AS sku,
    fixture.price,
    (
        12 + MOD(
            fixture.position * 7
                + expanded_size.size_position::INTEGER * 3
                + color.id::INTEGER,
            29
        )
    )::INTEGER AS stock_quantity
FROM dev_catalog_enriched_fixture fixture
JOIN products product ON product.slug = fixture.slug
JOIN dev_catalog_color_fixture color_fixture
  ON color_fixture.product_position = fixture.position
JOIN colors color ON color.name = color_fixture.color_name
CROSS JOIN LATERAL unnest(
    CASE fixture.size_family
        WHEN 'APPAREL' THEN ARRAY['XS', 'S', 'M', 'L', 'XL', 'XXL']
        WHEN 'SHOES' THEN ARRAY['35', '36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46']
        WHEN 'ONE_SIZE' THEN ARRAY['ONE SIZE']
        WHEN 'ADJUSTABLE' THEN ARRAY['ADJUSTABLE']
    END
) WITH ORDINALITY AS expanded_size(size_name, size_position)
JOIN sizes size ON size.name = expanded_size.size_name;

ALTER TABLE dev_catalog_expected_variant
    ADD PRIMARY KEY (product_id, color_id, size_id);
ALTER TABLE dev_catalog_expected_variant
    ADD CONSTRAINT uq_dev_catalog_expected_variant_sku UNIQUE (sku);

-- Temporarily free all SKU values in the owned scope, then reconcile by the
-- stable natural key so existing order/history foreign keys keep their IDs.
UPDATE product_variants variant
SET sku = 'DEV-ARCH-' || variant.id,
    updated_at = CURRENT_TIMESTAMP
FROM dev_catalog_known_product known
WHERE known.product_id = variant.product_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM dev_catalog_expected_variant expected
        JOIN product_variants variant ON variant.sku = expected.sku
        WHERE (variant.product_id, variant.color_id, variant.size_id)
            IS DISTINCT FROM
              (expected.product_id, expected.color_id, expected.size_id)
    ) THEN
        RAISE EXCEPTION 'Expected dev SKU is owned by a different variant';
    END IF;
END $$;

INSERT INTO product_variants (
    product_id, sku, price, stock_quantity, color_id, size_id, status, deleted_at
)
SELECT
    expected.product_id,
    expected.sku,
    expected.price,
    expected.stock_quantity,
    expected.color_id,
    expected.size_id,
    'ACTIVE',
    NULL
FROM dev_catalog_expected_variant expected
ON CONFLICT (product_id, color_id, size_id) DO UPDATE
SET sku = EXCLUDED.sku,
    price = EXCLUDED.price,
    stock_quantity = EXCLUDED.stock_quantity,
    status = EXCLUDED.status,
    deleted_at = NULL,
    updated_at = CURRENT_TIMESTAMP;

UPDATE product_variants variant
SET status = 'DISCONTINUED',
    deleted_at = COALESCE(variant.deleted_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
FROM dev_catalog_known_product known
WHERE known.product_id = variant.product_id
  AND NOT EXISTS (
      SELECT 1
      FROM dev_catalog_expected_variant expected
      WHERE expected.product_id = variant.product_id
        AND expected.color_id = variant.color_id
        AND expected.size_id = variant.size_id
  );

DELETE FROM product_images image
USING dev_catalog_known_product known
WHERE known.product_id = image.product_id;

WITH expanded_image AS (
    SELECT
        fixture.product_position,
        fixture.color_name,
        image_path,
        image_position
    FROM dev_catalog_color_fixture fixture
    CROSS JOIN LATERAL unnest(fixture.image_paths)
        WITH ORDINALITY AS image_fixture(image_path, image_position)
)
INSERT INTO product_images (
    product_id, variant_id, image, is_thumbnail, sort_order
)
SELECT
    expected.product_id,
    variant.id,
    '/uploads/products/' || image.image_path,
    image.image_position = 1,
    image.image_position::INTEGER
FROM expanded_image image
JOIN colors color ON color.name = image.color_name
JOIN dev_catalog_expected_variant expected
  ON expected.product_position = image.product_position
 AND expected.color_id = color.id
 AND expected.size_position = 1
JOIN product_variants variant
  ON variant.product_id = expected.product_id
 AND variant.color_id = expected.color_id
 AND variant.size_id = expected.size_id
 AND variant.deleted_at IS NULL
 AND variant.status = 'ACTIVE';

DELETE FROM product_attributes attribute
USING dev_catalog_known_product known
WHERE known.product_id = attribute.product_id
  AND attribute.name ~ '^Attribute [0-9]+$'
  AND attribute.value ~ '^Value [0-9]+$';

INSERT INTO product_attributes (product_id, name, value)
SELECT product.id, 'Material', fixture.material_en
FROM dev_catalog_enriched_fixture fixture
JOIN products product ON product.slug = fixture.slug
ON CONFLICT (product_id, name) DO UPDATE
SET value = EXCLUDED.value;

DO $$
BEGIN
    IF (
        SELECT count(*)
        FROM products product
        JOIN product_attributes owner
          ON owner.product_id = product.id
         AND owner.name = 'SeedOwner'
         AND owner.value = 'R3_PRODUCT_CATALOG_100'
        WHERE product.status = 'ACTIVE'
          AND product.deleted_at IS NULL
    ) <> 100 THEN
        RAISE EXCEPTION 'Expected exactly 100 active owned catalog products';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM dev_catalog_color_fixture fixture
        LEFT JOIN (
            SELECT
                image.product_id,
                variant.color_id,
                count(DISTINCT image.image) AS image_count
            FROM product_images image
            JOIN product_variants variant ON variant.id = image.variant_id
            GROUP BY image.product_id, variant.color_id
        ) gallery
          ON gallery.product_id = (
              SELECT product.id
              FROM products product
              JOIN dev_catalog_product_fixture product_fixture
                ON product_fixture.slug = product.slug
              WHERE product_fixture.position = fixture.product_position
          )
         AND gallery.color_id = (
              SELECT color.id FROM colors color WHERE color.name = fixture.color_name
          )
        WHERE COALESCE(gallery.image_count, 0) < 3
    ) THEN
        RAISE EXCEPTION 'Every dev product-color must have at least three images';
    END IF;
END $$;

DROP TABLE IF EXISTS dev_catalog_expected_variant;
DROP TABLE IF EXISTS dev_catalog_known_product;
DROP TABLE IF EXISTS dev_catalog_color_fixture;
DROP TABLE IF EXISTS dev_catalog_enriched_fixture;
DROP TABLE IF EXISTS dev_catalog_product_fixture;
