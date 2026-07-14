package vn.conganh.commercial;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DisplayName("System/Database - Seed data môi trường dev")
class DevSeedDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
}
