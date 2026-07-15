package vn.conganh.commercial.feature.review;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.TestDataFactory;

@Transactional
@DisplayName("Module Review - ReviewController")
class ReviewControllerTest extends AuthenticatedIntegrationTest {

        @Autowired
    private TestDataFactory testDataFactory;

    private String adminToken;
    private String forbiddenToken;

    @BeforeEach
    void setUp() {
        testDataFactory.seedPermissions("REVIEW", BASE_PATH, "GET", "POST");
        testDataFactory.seedPermissions("REVIEW", BASE_PATH + "/order-item/{orderItemId}", "GET");
        testDataFactory.seedPermissions("REVIEW", BASE_PATH + "/order/{orderId}", "GET");
        testDataFactory.seedPermissions("REVIEW", BASE_PATH + "/user/{userId}", "GET");

        adminToken = testDataFactory.jwtWithPermission();
        forbiddenToken = testDataFactory.jwtWithoutPermission();
    }

    @AfterEach
    void tearDown() {
        testDataFactory.cleanup();
    }

    @Override
    protected String adminToken() {
        return adminToken;
    }


    private static final String BASE_PATH = "/api/v1/reviews";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET / - 200: admin xem danh sách review")
    void getList_authenticatedAdmin_returnsList() throws Exception {
        // Arrange
        seedReview();

        // Act & Assert
        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET / - 401: từ chối request không có access token")
    void getList_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET / - 403: từ chối token hợp lệ nhưng không có quyền")
    void getList_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + noAccessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST / - 401: từ chối request tạo review không có access token")
    void create_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST / - 403: từ chối token hợp lệ nhưng không có quyền tạo review")
    void create_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", "Bearer " + noAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /user/{userId} - 200: admin xem review theo user")
    void getReviewsByUser_existingUser_returnsList() throws Exception {
        // Arrange
        ReviewSeed review = seedReview();

        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/user/" + review.userId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.result[0].productId").value(review.productId()))
                .andExpect(jsonPath("$.data.result[0].productSlug").isNotEmpty());
    }

    @Test
    @DisplayName("GET /product/{productId} - 200: khách xem review công khai theo sản phẩm")
    void getReviewsByProduct_publicRequest_returnsList() throws Exception {
        ReviewSeed review = seedReview();

        mockMvc.perform(get(BASE_PATH + "/product/" + review.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.result[0].productId").value(review.productId()))
                .andExpect(jsonPath("$.data.result[0].productSlug").isNotEmpty());
    }

    @Test
    @DisplayName("GET /order/{orderId} - 200: admin xem review theo order")
    void getReviewsByOrder_existingOrder_returnsList() throws Exception {
        // Arrange
        ReviewSeed review = seedReview();

        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/order/" + review.orderId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /order-item/{orderItemId} - 200: admin xem review theo order item")
    void getReviewsByOrderItem_existingOrderItem_returnsList() throws Exception {
        // Arrange
        ReviewSeed review = seedReview();

        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/order-item/" + review.orderItemId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    private ReviewSeed seedReview() {
        Long userId = insertUserRow(unique("review-user"));
        Long categoryId = seedCategory();
        Long brandId = seedBrand();
        Long productId = seedProduct(categoryId, brandId);
        Long colorId = seedColor();
        Long sizeId = seedSize();
        Long variantId = seedVariant(productId, colorId, sizeId);
        Long orderId = insertOrderRow(userId, unique("review-order").toUpperCase(), "COMPLETED");
        Long orderItemId = insertForId("""
                insert into order_items (order_id, variant_id, product_name, variant_name, sku, image,
                    list_price, price, price_source, quantity, subtotal, status)
                values (?, ?, 'Review Product', 'Black / M', ?, null,
                    100000, 100000, 'BASE', 1, 100000, 'CONFIRMED')
                returning id
                """, orderId, variantId, unique("review-sku").toUpperCase());
        Long reviewId = insertForId("""
                insert into reviews (user_id, order_item_id, rating, comment)
                values (?, ?, 5, 'Good product')
                returning id
                """, userId, orderItemId);
        return new ReviewSeed(reviewId, userId, orderId, orderItemId, productId);
    }

    private Long seedBrand() {
        String suffix = unique("brand");
        return insertForId("""
                insert into brands (name, slug, description, status)
                values (?, ?, 'Seed brand', 'ACTIVE')
                returning id
                """, "Brand " + suffix, suffix);
    }

    private Long seedCategory() {
        String suffix = unique("category");
        return insertForId("""
                insert into categories (name, slug, sort_order, status)
                values (?, ?, 1, 'ACTIVE')
                returning id
                """, "Category " + suffix, suffix);
    }

    private Long seedColor() {
        return insertForId("insert into colors (name, hex_code, sort_order) values (?, '#112233', 1) returning id",
                "Color " + unique("color"));
    }

    private Long seedSize() {
        return insertForId("insert into sizes (name, sort_order) values (?, 1) returning id", "Size " + unique("size"));
    }

    private Long seedProduct(Long categoryId, Long brandId) {
        String suffix = unique("product");
        return insertForId("""
                insert into products (name, slug, description, category_id, brand_id, status)
                values (?, ?, 'Seed product', ?, ?, 'ACTIVE')
                returning id
                """, "Product " + suffix, suffix, categoryId, brandId);
    }

    private Long seedVariant(Long productId, Long colorId, Long sizeId) {
        return insertForId("""
                insert into product_variants (product_id, sku, price, stock_quantity, color_id, size_id, status)
                values (?, ?, 100000, 5, ?, ?, 'ACTIVE')
                returning id
                """, productId, unique("variant").toUpperCase(), colorId, sizeId);
    }

    private Long insertUserRow(String suffix) {
        return insertForId("""
                insert into users (full_name, email, password, birth_date, gender)
                values (?, ?, '$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq',
                    date '1999-01-01', 'OTHER')
                returning id
                """, "User " + suffix, suffix + "@test.local");
    }

    private Long insertOrderRow(Long userId, String orderCode, String status) {
        return insertForId("""
                insert into orders (user_id, order_code, status, subtotal, shipping_fee, discount_amount,
                    final_amount, receiver_name, receiver_phone, receiver_address, payment_method, payment_status)
                values (?, ?, ?, 100000, 10000, 0, 110000, 'Receiver', '0900000000', '123 Test', 'COD', 'UNPAID')
                returning id
                """, userId, orderCode, status);
    }

    private Long insertForId(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private String noAccessToken() {
        return forbiddenToken;
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record ReviewSeed(Long reviewId, Long userId, Long orderId, Long orderItemId, Long productId) {
    }
}

