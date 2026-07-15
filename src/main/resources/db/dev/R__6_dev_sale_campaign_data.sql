-- Deterministic Sale Campaign fixtures.
--
-- Codes and SKUs are the natural keys. Active demo windows deliberately end
-- in 2099 because Flyway repeatable migrations do not rerun when time passes.

WITH campaign_fixture (
    code,
    name,
    description,
    banner_url,
    type,
    status,
    starts_at,
    ends_at,
    published_at,
    seeded_created_at
) AS (
    VALUES
        ('DEV-STANDARD-EVERYDAY',
         'Ưu đãi mỗi ngày',
         'Giá ưu đãi dài hạn dành cho các sản phẩm thiết yếu của VelaWear.',
         NULL::VARCHAR,
         'STANDARD',
         'PUBLISHED',
         TIMESTAMPTZ '2025-01-01 00:00:00+07',
         TIMESTAMPTZ '2099-12-31 23:59:59+07',
         TIMESTAMPTZ '2025-01-01 00:00:00+07',
         TIMESTAMPTZ '2024-12-15 09:00:00+07'),
        ('DEV-FLASH-DEAL',
         'Flash Sale nổi bật',
         'Ưu đãi số lượng giới hạn cho các thiết kế nổi bật.',
         NULL::VARCHAR,
         'FLASH',
         'PUBLISHED',
         TIMESTAMPTZ '2025-01-01 00:00:00+07',
         TIMESTAMPTZ '2099-12-31 23:59:59+07',
         TIMESTAMPTZ '2025-01-01 00:00:00+07',
         TIMESTAMPTZ '2024-12-20 09:00:00+07'),
        ('DEV-DRAFT-WEEKEND',
         'Ưu đãi cuối tuần bản nháp',
         'Campaign bản nháp để kiểm thử luồng chỉnh sửa và xuất bản trong Admin.',
         NULL::VARCHAR,
         'STANDARD',
         'DRAFT',
         TIMESTAMPTZ '2099-11-01 00:00:00+07',
         TIMESTAMPTZ '2099-11-03 23:59:59+07',
         NULL::TIMESTAMPTZ,
         TIMESTAMPTZ '2026-07-15 09:00:00+07'),
        ('DEV-HISTORICAL-SALE',
         'Ưu đãi lưu trữ 2024',
         'Campaign đã kết thúc để kiểm thử lịch sử và trạng thái ENDED.',
         NULL::VARCHAR,
         'STANDARD',
         'PUBLISHED',
         TIMESTAMPTZ '2024-01-01 00:00:00+07',
         TIMESTAMPTZ '2024-01-31 23:59:59+07',
         TIMESTAMPTZ '2024-01-01 00:00:00+07',
         TIMESTAMPTZ '2023-12-15 09:00:00+07')
), actor AS (
    SELECT id
    FROM users
    WHERE email = 'admin@velawear.local'
)
INSERT INTO sale_campaigns (
    code,
    name,
    description,
    banner_url,
    type,
    status,
    starts_at,
    ends_at,
    created_by,
    published_by,
    published_at,
    cancelled_at,
    created_at,
    updated_at
)
SELECT
    fixture.code,
    fixture.name,
    fixture.description,
    fixture.banner_url,
    fixture.type,
    fixture.status,
    fixture.starts_at,
    fixture.ends_at,
    actor.id,
    CASE WHEN fixture.status = 'PUBLISHED' THEN actor.id ELSE NULL END,
    fixture.published_at,
    NULL,
    fixture.seeded_created_at,
    CURRENT_TIMESTAMP
FROM campaign_fixture fixture
CROSS JOIN actor
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    banner_url = EXCLUDED.banner_url,
    type = EXCLUDED.type,
    status = EXCLUDED.status,
    starts_at = EXCLUDED.starts_at,
    ends_at = EXCLUDED.ends_at,
    created_by = EXCLUDED.created_by,
    published_by = EXCLUDED.published_by,
    published_at = EXCLUDED.published_at,
    cancelled_at = EXCLUDED.cancelled_at,
    version = sale_campaigns.version + 1
WHERE (
    sale_campaigns.name,
    sale_campaigns.description,
    sale_campaigns.banner_url,
    sale_campaigns.type,
    sale_campaigns.status,
    sale_campaigns.starts_at,
    sale_campaigns.ends_at,
    sale_campaigns.created_by,
    sale_campaigns.published_by,
    sale_campaigns.published_at,
    sale_campaigns.cancelled_at
) IS DISTINCT FROM (
    EXCLUDED.name,
    EXCLUDED.description,
    EXCLUDED.banner_url,
    EXCLUDED.type,
    EXCLUDED.status,
    EXCLUDED.starts_at,
    EXCLUDED.ends_at,
    EXCLUDED.created_by,
    EXCLUDED.published_by,
    EXCLUDED.published_at,
    EXCLUDED.cancelled_at
);

WITH translation_fixture (campaign_code, locale_code, name, description) AS (
    VALUES
        ('DEV-STANDARD-EVERYDAY', 'vi',
         'Ưu đãi mỗi ngày',
         'Giá ưu đãi dài hạn dành cho các sản phẩm thiết yếu của VelaWear.'),
        ('DEV-STANDARD-EVERYDAY', 'en',
         'Everyday Offers',
         'Long-running promotional prices on VelaWear everyday essentials.'),

        ('DEV-FLASH-DEAL', 'vi',
         'Flash Sale nổi bật',
         'Ưu đãi số lượng giới hạn cho các thiết kế nổi bật.'),
        ('DEV-FLASH-DEAL', 'en',
         'Featured Flash Sale',
         'Limited-quantity deals on selected standout designs.'),

        ('DEV-DRAFT-WEEKEND', 'vi',
         'Ưu đãi cuối tuần bản nháp',
         'Campaign bản nháp để kiểm thử luồng chỉnh sửa và xuất bản trong Admin.'),
        ('DEV-DRAFT-WEEKEND', 'en',
         'Draft Weekend Offer',
         'A draft campaign for testing the Admin editing and publishing workflow.'),

        ('DEV-HISTORICAL-SALE', 'vi',
         'Ưu đãi lưu trữ 2024',
         'Campaign đã kết thúc để kiểm thử lịch sử và trạng thái ENDED.'),
        ('DEV-HISTORICAL-SALE', 'en',
         'Archived Sale 2024',
         'An ended campaign for testing historical views and the ENDED phase.')
)
INSERT INTO sale_campaign_translations (
    campaign_id,
    locale_code,
    name,
    description,
    created_at,
    updated_at
)
SELECT
    campaign.id,
    fixture.locale_code,
    fixture.name,
    fixture.description,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM translation_fixture fixture
JOIN sale_campaigns campaign ON campaign.code = fixture.campaign_code
JOIN locales locale ON locale.code = fixture.locale_code
ON CONFLICT (campaign_id, locale_code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP
WHERE (
    sale_campaign_translations.name,
    sale_campaign_translations.description
) IS DISTINCT FROM (
    EXCLUDED.name,
    EXCLUDED.description
);

WITH item_fixture (
    campaign_code,
    sku,
    promotional_price,
    quota,
    max_per_customer
) AS (
    VALUES
        ('DEV-STANDARD-EVERYDAY', 'VW-TEE-BLK-M', 219000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-STANDARD-EVERYDAY', 'VW-TEE-RED-L', 219000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-STANDARD-EVERYDAY', 'NS-JACKET-PUR-M', 999000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-STANDARD-EVERYDAY', 'SV-TOTE-ORG-OS', 199000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-FLASH-DEAL', 'VW-SNK-WHT-39', 999000.00::NUMERIC, 30, 2),
        ('DEV-FLASH-DEAL', 'VW-TRS-BLK-M', 699000.00::NUMERIC, 50, 2),
        ('DEV-DRAFT-WEEKEND', 'VW-SNK-WHT-40', 1099000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-HISTORICAL-SALE', 'UT-DRESS-YLW-S', 599000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER),
        ('DEV-HISTORICAL-SALE', 'VW-TEE-BLK-M', 219000.00::NUMERIC, NULL::INTEGER, NULL::INTEGER)
)
INSERT INTO sale_campaign_items (
    campaign_id,
    variant_id,
    reference_price,
    promotional_price,
    quota,
    max_per_customer
)
SELECT
    campaign.id,
    variant.id,
    variant.price,
    fixture.promotional_price,
    fixture.quota,
    fixture.max_per_customer
FROM item_fixture fixture
JOIN sale_campaigns campaign ON campaign.code = fixture.campaign_code
JOIN product_variants variant ON variant.sku = fixture.sku
ON CONFLICT (campaign_id, variant_id) DO UPDATE
SET
    reference_price = EXCLUDED.reference_price,
    promotional_price = EXCLUDED.promotional_price,
    quota = CASE
        WHEN EXCLUDED.quota IS NULL THEN NULL
        ELSE GREATEST(
            COALESCE(sale_campaign_items.quota, 0),
            EXCLUDED.quota,
            sale_campaign_items.reserved_quantity + sale_campaign_items.sold_quantity
        )
    END,
    max_per_customer = EXCLUDED.max_per_customer
WHERE sale_campaign_items.reference_price IS DISTINCT FROM EXCLUDED.reference_price
   OR sale_campaign_items.promotional_price IS DISTINCT FROM EXCLUDED.promotional_price
   OR sale_campaign_items.max_per_customer IS DISTINCT FROM EXCLUDED.max_per_customer
   OR (EXCLUDED.quota IS NULL AND sale_campaign_items.quota IS NOT NULL)
   OR (
       EXCLUDED.quota IS NOT NULL
       AND (
           sale_campaign_items.quota IS NULL
           OR sale_campaign_items.quota < GREATEST(
               EXCLUDED.quota,
               sale_campaign_items.reserved_quantity + sale_campaign_items.sold_quantity
           )
       )
   );

-- The following orders already use the legacy promotional amounts. Reconcile
-- their immutable price snapshots with the STANDARD campaign without changing
-- order totals: list_price becomes the reference price, while price/subtotal
-- remain the amount actually paid.
WITH discounted_order_fixture (order_code, sku, campaign_code) AS (
    VALUES
        ('VW-DEV-1001', 'VW-TEE-BLK-M', 'DEV-HISTORICAL-SALE'),
        ('VW-DEV-1002', 'NS-JACKET-PUR-M', 'DEV-STANDARD-EVERYDAY'),
        ('VW-CONGANH-1001', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
        ('VW-CONGANH-1001', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY'),
        ('VW-CONGANH-PENDING', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
        ('VW-CONGANH-CONFIRMED', 'VW-TEE-RED-L', 'DEV-STANDARD-EVERYDAY'),
        ('VW-CONGANH-CANCELLED', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY')
)
UPDATE order_items order_item
SET
    list_price = campaign_item.reference_price,
    price = campaign_item.promotional_price,
    subtotal = campaign_item.promotional_price * order_item.quantity,
    price_source = 'STANDARD_SALE',
    sale_campaign_item_id = campaign_item.id,
    sale_campaign_code = campaign.code,
    sale_campaign_name = campaign.name
FROM discounted_order_fixture fixture
JOIN orders customer_order ON customer_order.order_code = fixture.order_code
JOIN sale_campaigns campaign ON campaign.code = fixture.campaign_code
JOIN product_variants variant ON variant.sku = fixture.sku
JOIN sale_campaign_items campaign_item
    ON campaign_item.campaign_id = campaign.id
   AND campaign_item.variant_id = variant.id
WHERE order_item.order_id = customer_order.id
  AND order_item.sku = fixture.sku;

-- Keep the historical fixture inside its campaign window so reporting and
-- audit screens demonstrate a real ended-campaign purchase.
UPDATE orders
SET created_at = TIMESTAMPTZ '2024-01-15 09:00:00+07'
WHERE order_code = 'VW-DEV-1001';

UPDATE payments payment
SET paid_at = TIMESTAMPTZ '2024-01-15 09:05:00+07'
FROM orders customer_order
WHERE payment.order_id = customer_order.id
  AND customer_order.order_code = 'VW-DEV-1001';
