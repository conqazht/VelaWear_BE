package vn.conganh.commercial;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("System/Database - Seed data môi trường dev")
class DevSeedDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void enableDevSeedData(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
    }

    // ========== Module Database: db/dev seed data ==========

    @Test
    @DisplayName("Dev seed - nạp đủ dữ liệu mẫu cho các module commerce")
    void devSeedData_loadsCompleteCommerceRelationships() {
        // Act
        Integer userCount = jdbcTemplate.queryForObject(
                "select count(*) from users where email like '%@velawear.local'",
                Integer.class);
        Integer productCount = jdbcTemplate.queryForObject("select count(*) from products", Integer.class);
        Integer orderCount = jdbcTemplate.queryForObject(
                "select count(*) from orders where order_code like 'VW-DEV-%'",
                Integer.class);
        Integer reviewCount = jdbcTemplate.queryForObject("select count(*) from reviews", Integer.class);
        Integer couponUsageCount = jdbcTemplate.queryForObject("select count(*) from coupon_usages", Integer.class);

        // Assert
        assertThat(userCount).isNotNull().isGreaterThanOrEqualTo(6);
        assertThat(productCount).isNotNull().isGreaterThanOrEqualTo(4);
        assertThat(orderCount).isNotNull().isGreaterThanOrEqualTo(3);
        assertThat(reviewCount).isNotNull().isGreaterThanOrEqualTo(2);
        assertThat(couponUsageCount).isNotNull().isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Module Review/Order - review seed phải gắn đúng với order item")
    void devSeedData_reviewsAreLinkedToOrderItems() {
        // Act
        Integer danglingReviews = jdbcTemplate.queryForObject("""
                select count(*)
                from reviews r
                left join order_items oi on oi.id = r.order_item_id
                where oi.id is null
                """, Integer.class);

        // Assert
        assertThat(danglingReviews).isZero();
    }

    @Test
    @DisplayName("Product seed - size đúng loại và mỗi màu có gallery ảnh riêng, không trùng")
    void devSeedData_productSizesAndColorImagesAreConsistent() {
        Integer mixedSizeSystems = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select pv.product_id
                    from product_variants pv
                    join sizes s on s.id = pv.size_id
                    where pv.deleted_at is null
                    group by pv.product_id
                    having count(distinct case
                        when s.name ~ '^[0-9]+$' then 'NUMERIC'
                        when s.name in ('XS', 'S', 'M', 'L', 'XL', 'XXL') then 'APPAREL'
                        else 'ACCESSORY'
                    end) > 1
                ) mixed
                """, Integer.class);
        Integer categorySizeMismatches = jdbcTemplate.queryForObject("""
                select count(*)
                from product_variants pv
                join products p on p.id = pv.product_id
                join categories c on c.id = p.category_id
                join sizes s on s.id = pv.size_id
                where pv.deleted_at is null
                  and (
                      (c.slug in ('t-shirts', 'dresses', 'jackets', 'ao', 'quan', 'vay', 'dam', 'ao-khoac')
                          and s.name not in ('XS', 'S', 'M', 'L', 'XL', 'XXL'))
                      or (c.slug = 'giay' and s.name !~ '^[0-9]+$')
                      or (c.slug in ('accessories', 'phu-kien')
                          and s.name not in ('ONE SIZE', 'ADJUSTABLE', 'REGULAR', 'LARGE'))
                  )
                """, Integer.class);
        Integer colorsWithoutImages = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select pv.product_id, pv.color_id
                    from product_variants pv
                    where pv.deleted_at is null
                      and pv.color_id is not null
                    group by pv.product_id, pv.color_id
                    except
                    select pi.product_id, image_variant.color_id
                    from product_images pi
                    join product_variants image_variant on image_variant.id = pi.variant_id
                    where image_variant.color_id is not null
                    group by pi.product_id, image_variant.color_id
                ) missing
                """, Integer.class);
        Integer duplicateColorImageUrls = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select pi.product_id, image_variant.color_id, pi.image
                    from product_images pi
                    join product_variants image_variant on image_variant.id = pi.variant_id
                    where image_variant.color_id is not null
                    group by pi.product_id, image_variant.color_id, pi.image
                    having count(*) > 1
                ) duplicates
                """, Integer.class);
        Integer colorsWithSparseGalleries = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select pv.product_id, pv.color_id
                    from product_variants pv
                    where pv.deleted_at is null
                      and pv.color_id is not null
                    group by pv.product_id, pv.color_id
                ) product_colors
                left join (
                    select pi.product_id, image_variant.color_id, count(distinct pi.image) as image_count
                    from product_images pi
                    join product_variants image_variant on image_variant.id = pi.variant_id
                    where image_variant.color_id is not null
                    group by pi.product_id, image_variant.color_id
                ) galleries
                  on galleries.product_id = product_colors.product_id
                 and galleries.color_id = product_colors.color_id
                where coalesce(galleries.image_count, 0) < 3
                """, Integer.class);

        assertThat(mixedSizeSystems).isZero();
        assertThat(categorySizeMismatches).isZero();
        assertThat(colorsWithoutImages).isZero();
        assertThat(duplicateColorImageUrls).isZero();
        assertThat(colorsWithSparseGalleries).isZero();
    }

    @Test
    @DisplayName("Catalog i18n seed - mọi sản phẩm và danh mục có đủ VI/EN, slug không trùng")
    void devSeedData_catalogTranslationsCoverAllSeededContent() {
        Integer productCount = jdbcTemplate.queryForObject("select count(*) from products", Integer.class);
        Integer categoryCount = jdbcTemplate.queryForObject("select count(*) from categories", Integer.class);
        Integer enabledLocaleCount = jdbcTemplate.queryForObject("""
                select count(*)
                from locales
                where code in ('vi', 'en')
                  and is_enabled = true
                """, Integer.class);
        Boolean vietnameseIsDefault = jdbcTemplate.queryForObject("""
                select is_default
                from locales
                where code = 'vi'
                """, Boolean.class);
        Boolean englishIsDefault = jdbcTemplate.queryForObject("""
                select is_default
                from locales
                where code = 'en'
                """, Boolean.class);
        Integer missingProductTranslations = jdbcTemplate.queryForObject("""
                select count(*)
                from products product
                cross join (values ('vi'), ('en')) requested_locale(code)
                where not exists (
                    select 1
                    from product_translations translation
                    where translation.product_id = product.id
                      and translation.locale_code = requested_locale.code
                )
                """, Integer.class);
        Integer missingCategoryTranslations = jdbcTemplate.queryForObject("""
                select count(*)
                from categories category
                cross join (values ('vi'), ('en')) requested_locale(code)
                where not exists (
                    select 1
                    from category_translations translation
                    where translation.category_id = category.id
                      and translation.locale_code = requested_locale.code
                )
                """, Integer.class);
        Integer duplicateLocalizedSlugs = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select locale_code, slug
                    from product_translations
                    group by locale_code, slug
                    having count(*) > 1

                    union all

                    select locale_code, slug
                    from category_translations
                    group by locale_code, slug
                    having count(*) > 1
                ) duplicate_slug
                """, Integer.class);

        assertThat(productCount).isEqualTo(6);
        assertThat(categoryCount).isEqualTo(14);
        assertThat(enabledLocaleCount).isEqualTo(2);
        assertThat(vietnameseIsDefault).isTrue();
        assertThat(englishIsDefault).isFalse();
        assertThat(missingProductTranslations).isZero();
        assertThat(missingCategoryTranslations).isZero();
        assertThat(duplicateLocalizedSlugs).isZero();
    }

    @Test
    @DisplayName("Sale seed - có STANDARD, FLASH, DRAFT, lịch sử và đủ bản dịch VI/EN")
    void devSeedData_saleCampaignsCoverStorefrontAndAdminScenarios() {
        Integer campaignCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaigns
                where code in (
                    'DEV-STANDARD-EVERYDAY',
                    'DEV-FLASH-DEAL',
                    'DEV-DRAFT-WEEKEND',
                    'DEV-HISTORICAL-SALE'
                )
                """, Integer.class);
        Integer missingCampaignTranslations = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaigns campaign
                cross join (values ('vi'), ('en')) requested_locale(code)
                where campaign.code like 'DEV-%'
                  and not exists (
                    select 1
                    from sale_campaign_translations translation
                    where translation.campaign_id = campaign.id
                      and translation.locale_code = requested_locale.code
                )
                """, Integer.class);
        Integer liveCampaignTypeCount = jdbcTemplate.queryForObject("""
                select count(distinct type)
                from sale_campaigns
                where code in ('DEV-STANDARD-EVERYDAY', 'DEV-FLASH-DEAL')
                  and status = 'PUBLISHED'
                  and starts_at <= current_timestamp
                  and ends_at > current_timestamp
                """, Integer.class);
        Integer seededItemCount = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaign_items item
                join sale_campaigns campaign on campaign.id = item.campaign_id
                where campaign.code like 'DEV-%'
                """, Integer.class);
        Integer invalidReferencePrices = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaign_items item
                join sale_campaigns campaign on campaign.id = item.campaign_id
                join product_variants variant on variant.id = item.variant_id
                where campaign.code like 'DEV-%'
                  and (
                      item.reference_price <> variant.price
                      or item.promotional_price >= item.reference_price
                  )
                """, Integer.class);
        Integer invalidFlashLimits = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaign_items item
                join sale_campaigns campaign on campaign.id = item.campaign_id
                where campaign.code = 'DEV-FLASH-DEAL'
                  and (
                      item.quota is null
                      or item.max_per_customer is null
                      or item.max_per_customer > item.quota
                  )
                """, Integer.class);

        assertThat(campaignCount).isEqualTo(4);
        assertThat(missingCampaignTranslations).isZero();
        assertThat(liveCampaignTypeCount).isEqualTo(2);
        assertThat(seededItemCount).isEqualTo(9);
        assertThat(invalidReferencePrices).isZero();
        assertThat(invalidFlashLimits).isZero();
    }

    @Test
    @DisplayName("Order seed - giá sale cũ được lưu đúng snapshot và liên kết campaign")
    void devSeedData_legacySalePricesUseCampaignSnapshots() {
        Integer migratedOrderItemCount = jdbcTemplate.queryForObject("""
                with expected(order_code, sku, campaign_code) as (
                    values
                        ('VW-DEV-1001', 'VW-TEE-BLK-M', 'DEV-HISTORICAL-SALE'),
                        ('VW-DEV-1002', 'NS-JACKET-PUR-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-1001', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-1001', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-PENDING', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-CONFIRMED', 'VW-TEE-RED-L', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-CANCELLED', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY')
                )
                select count(*)
                from expected
                join orders customer_order on customer_order.order_code = expected.order_code
                join order_items item
                  on item.order_id = customer_order.id
                 and item.sku = expected.sku
                 and item.sale_campaign_code = expected.campaign_code
                """, Integer.class);
        Integer invalidSaleSnapshots = jdbcTemplate.queryForObject("""
                with expected(order_code, sku, campaign_code) as (
                    values
                        ('VW-DEV-1001', 'VW-TEE-BLK-M', 'DEV-HISTORICAL-SALE'),
                        ('VW-DEV-1002', 'NS-JACKET-PUR-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-1001', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-1001', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-PENDING', 'VW-TEE-BLK-M', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-CONFIRMED', 'VW-TEE-RED-L', 'DEV-STANDARD-EVERYDAY'),
                        ('VW-CONGANH-CANCELLED', 'SV-TOTE-ORG-OS', 'DEV-STANDARD-EVERYDAY')
                )
                select count(*)
                from expected
                join orders customer_order on customer_order.order_code = expected.order_code
                join order_items item on item.order_id = customer_order.id and item.sku = expected.sku
                join sale_campaign_items campaign_item on campaign_item.id = item.sale_campaign_item_id
                join sale_campaigns campaign on campaign.id = campaign_item.campaign_id
                where campaign.code <> expected.campaign_code
                   or item.price_source <> 'STANDARD_SALE'
                   or item.list_price <> campaign_item.reference_price
                   or item.price <> campaign_item.promotional_price
                   or item.subtotal <> item.price * item.quantity
                   or item.sale_campaign_code <> campaign.code
                   or item.sale_campaign_name <> campaign.name
                """, Integer.class);
        Integer historicalOrdersOutsideWindow = jdbcTemplate.queryForObject("""
                select count(*)
                from order_items item
                join orders customer_order on customer_order.id = item.order_id
                join sale_campaigns campaign on campaign.code = item.sale_campaign_code
                where item.sale_campaign_code = 'DEV-HISTORICAL-SALE'
                  and (
                      customer_order.created_at < campaign.starts_at
                      or customer_order.created_at >= campaign.ends_at
                  )
                """, Integer.class);

        assertThat(migratedOrderItemCount).isEqualTo(7);
        assertThat(invalidSaleSnapshots).isZero();
        assertThat(historicalOrdersOutsideWindow).isZero();
    }

    @Test
    @DisplayName("Mock order seed - item/header/payment nhất quán và block chạy lại không phình")
    void devSeedData_mockOrderBlockIsConsistentAndIdempotent() throws Exception {
        Integer mockItemsBefore = mockOrderItemCount();
        Integer mockPaymentsBefore = mockPaymentCount();
        Integer saleFixturesBefore = knownSaleFixtureItemCount();

        assertThat(mockItemsBefore).isEqualTo(100);
        assertThat(mockPaymentsBefore).isEqualTo(100);
        assertMockOrderAccountingIsConsistent();

        runSqlSection(
                "db/dev/R__3_dev_large_mock_data.sql",
                "-- 12. Orders (100 records)",
                "-- 16. Coupon Usages (100 records)");

        assertThat(mockOrderItemCount()).isEqualTo(mockItemsBefore);
        assertThat(mockPaymentCount()).isEqualTo(mockPaymentsBefore);
        assertThat(knownSaleFixtureItemCount()).isEqualTo(saleFixturesBefore);
        assertMockOrderAccountingIsConsistent();
    }

    @Test
    @DisplayName("Catalog i18n seed - tự phục hồi khi core dùng slug VI và EN từng là default")
    void devSeedData_catalogSeedRecoversFromLocalizedCoreSlugsAndDefaultDrift() {
        Long productId = jdbcTemplate.queryForObject("""
                select product_id
                from product_translations
                where locale_code = 'vi' and slug = 'ao-thun-cotton-thiet-yeu'
                """, Long.class);
        Long categoryId = jdbcTemplate.queryForObject("""
                select category_id
                from category_translations
                where locale_code = 'vi' and slug = 'nam'
                """, Long.class);

        try {
            jdbcTemplate.update("update products set slug = 'ao-thun-cotton-thiet-yeu' where id = ?", productId);
            jdbcTemplate.update("update categories set slug = 'nam' where id = ?", categoryId);
            jdbcTemplate.update(
                    "delete from product_translations where product_id = ? and locale_code = 'en'",
                    productId);
            jdbcTemplate.update(
                    "delete from category_translations where category_id = ? and locale_code = 'en'",
                    categoryId);
            jdbcTemplate.update("update locales set is_default = false where code = 'vi'");
            jdbcTemplate.update("update locales set is_default = true where code = 'en'");

            runSeeds("db/dev/R__5_dev_catalog_i18n_data.sql");

            Integer restoredEnglishRows = jdbcTemplate.queryForObject("""
                    select
                        (select count(*) from product_translations
                         where product_id = ? and locale_code = 'en')
                        +
                        (select count(*) from category_translations
                         where category_id = ? and locale_code = 'en')
                    """, Integer.class, productId, categoryId);
            String coreProductSlug = jdbcTemplate.queryForObject(
                    "select slug from products where id = ?", String.class, productId);
            String coreCategorySlug = jdbcTemplate.queryForObject(
                    "select slug from categories where id = ?", String.class, categoryId);
            Boolean viDefault = jdbcTemplate.queryForObject(
                    "select is_default from locales where code = 'vi'", Boolean.class);
            Boolean enDefault = jdbcTemplate.queryForObject(
                    "select is_default from locales where code = 'en'", Boolean.class);

            assertThat(restoredEnglishRows).isEqualTo(2);
            assertThat(coreProductSlug).isEqualTo("ao-thun-cotton-thiet-yeu");
            assertThat(coreCategorySlug).isEqualTo("nam");
            assertThat(viDefault).isTrue();
            assertThat(enDefault).isFalse();
        } finally {
            jdbcTemplate.update("update products set slug = 'essential-cotton-tee' where id = ?", productId);
            jdbcTemplate.update("update categories set slug = 'men' where id = ?", categoryId);
            runSeeds("db/dev/R__5_dev_catalog_i18n_data.sql");
        }
    }

    @Test
    @DisplayName("Sale seed - không bump version khi không đổi và bump đúng một lần khi reconcile")
    void devSeedData_saleCampaignVersionOnlyChangesForBusinessDrift() {
        Long createdAtEpochBefore = campaignCreatedAtEpoch("DEV-STANDARD-EVERYDAY");
        Long unchangedVersionBefore = campaignVersion("DEV-STANDARD-EVERYDAY");

        runSeeds("db/dev/R__6_dev_sale_campaign_data.sql");

        assertThat(campaignVersion("DEV-STANDARD-EVERYDAY")).isEqualTo(unchangedVersionBefore);
        assertThat(campaignCreatedAtEpoch("DEV-STANDARD-EVERYDAY")).isEqualTo(createdAtEpochBefore);

        jdbcTemplate.update("""
                update sale_campaigns
                set description = 'manual development drift'
                where code = 'DEV-STANDARD-EVERYDAY'
                """);
        Long driftedVersion = campaignVersion("DEV-STANDARD-EVERYDAY");

        runSeeds("db/dev/R__6_dev_sale_campaign_data.sql");

        Long reconciledVersion = campaignVersion("DEV-STANDARD-EVERYDAY");
        assertThat(reconciledVersion).isEqualTo(driftedVersion + 1);
        assertThat(campaignCreatedAtEpoch("DEV-STANDARD-EVERYDAY")).isEqualTo(createdAtEpochBefore);

        runSeeds("db/dev/R__6_dev_sale_campaign_data.sql");
        assertThat(campaignVersion("DEV-STANDARD-EVERYDAY")).isEqualTo(reconciledVersion);
    }

    @Test
    @DisplayName("Repeatable seed VI/EN và Sale chạy lại không tạo bản ghi trùng")
    void devSeedData_catalogAndSaleScriptsAreIdempotent() {
        Integer translationsBefore = jdbcTemplate.queryForObject("""
                select
                    (select count(*) from product_translations)
                    + (select count(*) from category_translations)
                    + (select count(*) from sale_campaign_translations)
                """, Integer.class);
        Integer campaignsBefore = jdbcTemplate.queryForObject(
                "select count(*) from sale_campaigns where code like 'DEV-%'",
                Integer.class);
        Integer campaignItemsBefore = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaign_items item
                join sale_campaigns campaign on campaign.id = item.campaign_id
                where campaign.code like 'DEV-%'
                """, Integer.class);
        Long campaignVersionBefore = campaignVersion("DEV-STANDARD-EVERYDAY");
        Long campaignCreatedAtBefore = campaignCreatedAtEpoch("DEV-STANDARD-EVERYDAY");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/dev/R__5_dev_catalog_i18n_data.sql"),
                new ClassPathResource("db/dev/R__6_dev_sale_campaign_data.sql"));
        populator.execute(dataSource);

        Integer translationsAfter = jdbcTemplate.queryForObject("""
                select
                    (select count(*) from product_translations)
                    + (select count(*) from category_translations)
                    + (select count(*) from sale_campaign_translations)
                """, Integer.class);
        Integer campaignsAfter = jdbcTemplate.queryForObject(
                "select count(*) from sale_campaigns where code like 'DEV-%'",
                Integer.class);
        Integer campaignItemsAfter = jdbcTemplate.queryForObject("""
                select count(*)
                from sale_campaign_items item
                join sale_campaigns campaign on campaign.id = item.campaign_id
                where campaign.code like 'DEV-%'
                """, Integer.class);

        assertThat(translationsAfter).isEqualTo(translationsBefore);
        assertThat(campaignsAfter).isEqualTo(campaignsBefore);
        assertThat(campaignItemsAfter).isEqualTo(campaignItemsBefore);
        assertThat(campaignVersion("DEV-STANDARD-EVERYDAY")).isEqualTo(campaignVersionBefore);
        assertThat(campaignCreatedAtEpoch("DEV-STANDARD-EVERYDAY")).isEqualTo(campaignCreatedAtBefore);
    }

    @Test
    @DisplayName("Công Anh fixture - đủ trạng thái đơn và lịch sử coupon gắn đúng user")
    void devSeedData_congAnhHasOrderStatsAndCouponHistory() {
        Integer statusCount = jdbcTemplate.queryForObject("""
                select count(distinct status)
                from orders
                where order_code like 'VW-CONGANH-%'
                """, Integer.class);
        Integer couponHistoryCount = jdbcTemplate.queryForObject("""
                select count(*)
                from coupon_usages usage
                join orders o on o.id = usage.order_id
                join coupons c on c.id = usage.coupon_id
                where o.order_code like 'VW-CONGANH-%'
                  and c.code in ('CONGANH15', 'CONGANH20', 'CONGANH80K')
                """, Integer.class);
        Integer mismatchedUsers = jdbcTemplate.queryForObject("""
                select count(*)
                from coupon_usages usage
                join orders o on o.id = usage.order_id
                where o.order_code like 'VW-CONGANH-%'
                  and usage.user_id <> o.user_id
                """, Integer.class);

        assertThat(statusCount).isEqualTo(6);
        assertThat(couponHistoryCount).isEqualTo(2);
        assertThat(mismatchedUsers).isZero();
    }

    private Integer mockOrderItemCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from order_items item
                join orders customer_order on customer_order.id = item.order_id
                where customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
                """, Integer.class);
    }

    private Integer mockPaymentCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from payments payment
                join orders customer_order on customer_order.id = payment.order_id
                where customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
                """, Integer.class);
    }

    private Integer knownSaleFixtureItemCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from order_items item
                join orders customer_order on customer_order.id = item.order_id
                where (customer_order.order_code, item.sku) in (
                    ('VW-DEV-1001', 'VW-TEE-BLK-M'),
                    ('VW-DEV-1002', 'NS-JACKET-PUR-M'),
                    ('VW-CONGANH-1001', 'VW-TEE-BLK-M'),
                    ('VW-CONGANH-1001', 'SV-TOTE-ORG-OS'),
                    ('VW-CONGANH-PENDING', 'VW-TEE-BLK-M'),
                    ('VW-CONGANH-CONFIRMED', 'VW-TEE-RED-L'),
                    ('VW-CONGANH-CANCELLED', 'SV-TOTE-ORG-OS')
                )
                """, Integer.class);
    }

    private void assertMockOrderAccountingIsConsistent() {
        Integer mockOrderCount = jdbcTemplate.queryForObject("""
                select count(*)
                from orders
                where order_code ~ '^VW-MOCK-[0-9]+$'
                """, Integer.class);
        Integer invalidOrderHeaders = jdbcTemplate.queryForObject("""
                select count(*)
                from orders customer_order
                left join (
                    select order_id, sum(subtotal) as item_total
                    from order_items
                    group by order_id
                ) item_totals on item_totals.order_id = customer_order.id
                where customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
                  and (
                      customer_order.subtotal <> coalesce(item_totals.item_total, 0)
                      or customer_order.final_amount <>
                         customer_order.subtotal
                         + customer_order.shipping_fee
                         - customer_order.discount_amount
                  )
                """, Integer.class);
        Integer invalidPayments = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select
                        customer_order.id,
                        customer_order.final_amount,
                        count(payment.id) as payment_count,
                        max(payment.amount) as payment_amount
                    from orders customer_order
                    left join payments payment on payment.order_id = customer_order.id
                    where customer_order.order_code ~ '^VW-MOCK-[0-9]+$'
                    group by customer_order.id, customer_order.final_amount
                    having count(payment.id) <> 1
                        or max(payment.amount) <> customer_order.final_amount
                ) invalid
                """, Integer.class);

        assertThat(mockOrderCount).isEqualTo(100);
        assertThat(invalidOrderHeaders).isZero();
        assertThat(invalidPayments).isZero();
    }

    private void runSqlSection(String resourcePath, String startMarker, String endMarker) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        String script;
        try (var input = resource.getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        int start = script.indexOf(startMarker);
        int end = script.indexOf(endMarker, start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);

        byte[] section = script.substring(start, end).getBytes(StandardCharsets.UTF_8);
        new ResourceDatabasePopulator(new ByteArrayResource(section)).execute(dataSource);
    }

    private void runSeeds(String... resourcePaths) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        for (String resourcePath : resourcePaths) {
            populator.addScript(new ClassPathResource(resourcePath));
        }
        populator.execute(dataSource);
    }

    private Long campaignVersion(String code) {
        return jdbcTemplate.queryForObject(
                "select version from sale_campaigns where code = ?",
                Long.class,
                code);
    }

    private Long campaignCreatedAtEpoch(String code) {
        return jdbcTemplate.queryForObject(
                "select extract(epoch from created_at)::bigint from sale_campaigns where code = ?",
                Long.class,
                code);
    }
}
