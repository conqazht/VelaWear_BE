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
}
