package vn.conganh.commercial.feature.review;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
    @DisplayName("POST / - 201: khách đã đăng nhập tạo review multipart không cần quyền staff")
    void create_authenticatedCustomer_returnsCreatedReviewWithImage() throws Exception {
        String email = unique("review-customer") + "@local.test";
        Long userId = insertUserRowWithEmail(unique("review-customer"), email);
        ReviewOrderSeed orderSeed = seedReviewOrderForUser(userId, "COMPLETED");
        String token = tokenWithRoles(email, userId, List.of("ROLE_USER"));

        mockMvc.perform(multipart(BASE_PATH)
                        .file(reviewPart(orderSeed.orderItemId(), 5, "Excellent"))
                        .file(new MockMultipartFile(
                                "images", "review.jpg", "image/jpeg", jpegBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.orderItemId").value(orderSeed.orderItemId()))
                .andExpect(jsonPath("$.data.images[0]").value(org.hamcrest.Matchers.matchesPattern(
                        "/uploads/reviews/[0-9a-f-]+\\.jpg")));
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

        mockMvc.perform(get(BASE_PATH + "/product/" + review.productId())
                        .queryParam("rating", "5")
                        .queryParam("sort", "rating-high")
                        .queryParam("page", "1")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.result[0].productId").value(review.productId()))
                .andExpect(jsonPath("$.data.result[0].productSlug").isNotEmpty())
                .andExpect(jsonPath("$.data.result[0].verifiedPurchase").value(true))
                .andExpect(jsonPath("$.data.result[0].images[0]").value("/uploads/reviews/seed-review.jpg"))
                .andExpect(jsonPath("$.data.result[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.result[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.data.result[0].orderCode").doesNotExist())
                .andExpect(jsonPath("$.data.result[0].orderItemId").doesNotExist());
    }

    @Test
    @DisplayName("GET /product/{productId}/summary - 200: trả phân bố đủ 1 đến 5 sao")
    void getProductReviewSummary_publicRequest_returnsSummary() throws Exception {
        ReviewSeed review = seedReview();

        mockMvc.perform(get(BASE_PATH + "/product/" + review.productId() + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.ratingCounts.1").value(0))
                .andExpect(jsonPath("$.data.ratingCounts.5").value(1));
    }

    @Test
    @DisplayName("GET /me - 200: khách chỉ xem review của principal hiện tại")
    void getMyReviews_authenticatedCustomer_returnsOwnReviews() throws Exception {
        String email = unique("my-review") + "@local.test";
        Long userId = insertUserRowWithEmail(unique("my-review"), email);
        ReviewSeed review = seedReviewForUser(userId);
        String token = tokenWithRoles(email, userId, List.of("ROLE_USER"));

        mockMvc.perform(get(BASE_PATH + "/me")
                        .queryParam("orderId", review.orderId().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].userId").value(userId))
                .andExpect(jsonPath("$.data.result[0].orderId").value(review.orderId()));
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
        return seedReviewForUser(userId);
    }

    private ReviewSeed seedReviewForUser(Long userId) {
        ReviewOrderSeed orderSeed = seedReviewOrderForUser(userId, "COMPLETED");
        Long reviewId = insertForId("""
                insert into reviews (user_id, order_item_id, rating, comment)
                values (?, ?, 5, 'Good product')
                returning id
                """, userId, orderSeed.orderItemId());
        jdbcTemplate.update(
                "insert into review_images (review_id, image) values (?, '/uploads/reviews/seed-review.jpg')",
                reviewId);
        return new ReviewSeed(
                reviewId,
                userId,
                orderSeed.orderId(),
                orderSeed.orderItemId(),
                orderSeed.productId());
    }

    private ReviewOrderSeed seedReviewOrderForUser(Long userId, String orderStatus) {
        Long categoryId = seedCategory();
        Long brandId = seedBrand();
        Long productId = seedProduct(categoryId, brandId);
        Long colorId = seedColor();
        Long sizeId = seedSize();
        Long variantId = seedVariant(productId, colorId, sizeId);
        Long orderId = insertOrderRow(userId, unique("review-order").toUpperCase(), orderStatus);
        Long orderItemId = insertForId("""
                insert into order_items (order_id, variant_id, product_name, product_slug, variant_name, sku, image,
                    list_price, price, price_source, quantity, subtotal, status)
                values (?, ?, 'Review Product', 'review-product', 'Black / M', ?, null,
                    100000, 100000, 'BASE', 1, 100000, 'CONFIRMED')
                returning id
                """, orderId, variantId, unique("review-sku").toUpperCase());
        return new ReviewOrderSeed(orderId, orderItemId, productId);
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
        return insertForId(
                "insert into sizes (name, sort_order) values (?, 1) returning id",
                "Size " + unique("size"));
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
        return insertUserRowWithEmail(suffix, suffix + "@test.local");
    }

    private Long insertUserRowWithEmail(String suffix, String email) {
        return insertForId("""
                insert into users (full_name, email, password, birth_date, gender)
                values (?, ?, '$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq',
                    date '1999-01-01', 'OTHER')
                returning id
                """, "User " + suffix, email);
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

    private MockMultipartFile reviewPart(Long orderItemId, int rating, String comment) {
        String json = """
                {"orderItemId":%d,"rating":%d,"comment":"%s"}
                """.formatted(orderItemId, rating, comment);
        return new MockMultipartFile(
                "review",
                "review.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private record ReviewSeed(Long reviewId, Long userId, Long orderId, Long orderItemId, Long productId) {
    }

    private record ReviewOrderSeed(Long orderId, Long orderItemId, Long productId) {
    }
}

