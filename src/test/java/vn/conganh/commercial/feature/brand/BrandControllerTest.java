package vn.conganh.commercial.feature.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
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
@DisplayName("Module Brand - BrandController")
class BrandControllerTest extends AuthenticatedIntegrationTest {

        @Autowired
    private TestDataFactory testDataFactory;

    private String adminToken;
    private String forbiddenToken;

    @BeforeEach
    void setUp() {
        testDataFactory.seedPermissions("BRAND", BASE_PATH, "GET", "POST");
        testDataFactory.seedPermissions("BRAND", BASE_PATH + "/{id}", "DELETE", "GET", "PUT");

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


    private static final Long MISSING_ID = 999_999_999L;
    private static final String BASE_PATH = "/api/v1/brands";
    private static final String TABLE_NAME = "brands";
    private static final boolean SOFT_DELETE = true;
    private static final boolean HAS_UPDATED_AT = true;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("GET / - 200: admin xem danh sách dữ liệu")
    void getList_authenticatedAdmin_returnsList() throws Exception {
        // Arrange
        seedEntity();

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
    @DisplayName("GET /{id} - 200: admin xem chi tiết theo id")
    void getById_existingId_returnsDetail() throws Exception {
        // Arrange
        SeededEntity entity = seedEntity();

        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/" + entity.id())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(entity.id()));
    }

    @Test
    @DisplayName("GET /{id} - 401: từ chối request không có access token")
    void getById_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/" + MISSING_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id} - 403: từ chối token hợp lệ nhưng không có quyền")
    void getById_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + noAccessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /{id} - 404: trả về Not Found khi id không tồn tại")
    void getById_missingId_returnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(get(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("POST / - 401: từ chối request tạo mới không có access token")
    void create_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST / - 403: từ chối token hợp lệ nhưng không có quyền tạo mới")
    void create_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(post(BASE_PATH)
                        .header("Authorization", "Bearer " + noAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /{id} - 200: admin cập nhật dữ liệu")
    void update_existingId_returnsUpdatedData() throws Exception {
        // Arrange
        SeededEntity entity = seedEntity();
        Instant beforeUpdate = HAS_UPDATED_AT ? readInstant(TABLE_NAME, "updated_at", entity.id()) : null;

        // Act & Assert
        mockMvc.perform(put(BASE_PATH + "/" + entity.id())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entity.updateBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(entity.id()));

        entityManager.flush();
        if (HAS_UPDATED_AT) {
            assertThat(readInstant(TABLE_NAME, "updated_at", entity.id())).isAfter(beforeUpdate);
        }
    }

    @Test
    @DisplayName("PUT /{id} - 400: từ chối dữ liệu update sai định dạng")
    void update_invalidBody_returnsBadRequest() throws Exception {
        // Arrange
        SeededEntity entity = seedEntity();

        // Act & Assert
        mockMvc.perform(put(BASE_PATH + "/" + entity.id())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entity.invalidUpdateBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    @DisplayName("PUT /{id} - 401: từ chối request cập nhật không có access token")
    void update_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(put(BASE_PATH + "/" + MISSING_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /{id} - 403: từ chối token hợp lệ nhưng không có quyền cập nhật")
    void update_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(put(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + noAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /{id} - 404: trả về Not Found khi id không tồn tại")
    void update_missingId_returnsNotFound() throws Exception {
        // Arrange
        SeededEntity entity = seedEntity();

        // Act & Assert
        mockMvc.perform(put(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(entity.updateBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    @Test
    @DisplayName("DELETE /{id} - 200: admin xóa dữ liệu")
    void delete_existingId_removesOrSoftDeletesData() throws Exception {
        // Arrange
        SeededEntity entity = seedEntity();

        // Act & Assert
        mockMvc.perform(delete(BASE_PATH + "/" + entity.id())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        entityManager.flush();
        if (SOFT_DELETE) {
            assertThat(readInstant(TABLE_NAME, "deleted_at", entity.id())).isNotNull();
            mockMvc.perform(get(BASE_PATH + "/" + entity.id())
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.statusCode").value(404));
            return;
        }

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + TABLE_NAME + " where id = ?", Integer.class, entity.id());
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("DELETE /{id} - 401: từ chối request xóa không có access token")
    void delete_missingToken_returnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(delete(BASE_PATH + "/" + MISSING_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /{id} - 403: từ chối token hợp lệ nhưng không có quyền xóa")
    void delete_authenticatedRoleWithoutPermission_returnsForbidden() throws Exception {
        // Act & Assert
        mockMvc.perform(delete(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + noAccessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /{id} - 404: trả về Not Found khi id không tồn tại")
    void delete_missingId_returnsNotFound() throws Exception {
        // Act & Assert
        mockMvc.perform(delete(BASE_PATH + "/" + MISSING_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    private SeededEntity seedEntity() {
        return seedBrand();
    }

    private SeededEntity seedBrand() {
        String suffix = unique("brand");
        Long id = insertForId("""
                insert into brands (name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, "Brand " + suffix, suffix, "Seed brand");
        return new SeededEntity(id,
                json("name", "Brand Updated " + suffix, "description", "Updated", "status", "INACTIVE"),
                json("name", "", "description", "Invalid", "status", "ACTIVE"));
    }

    private SeededEntity seedCart() {
        Long userId = insertUserRow(unique("cart-user"));
        Long id = insertForId("insert into carts (user_id) values (?) returning id", userId);
        return new SeededEntity(id, "", "");
    }

    private SeededEntity seedCategory() {
        String suffix = unique("category");
        Long id = insertForId("""
                insert into categories (name, slug, sort_order, status, created_at, updated_at)
                values (?, ?, 1, 'ACTIVE', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, "Category " + suffix, suffix);
        return new SeededEntity(id,
                json("parentId", null, "name", "Category Updated " + suffix, "sortOrder", 2, "status", "INACTIVE"),
                json("parentId", null, "name", "", "sortOrder", 2, "status", "ACTIVE"));
    }

    private SeededEntity seedColor() {
        String suffix = unique("color");
        Long id = insertForId("insert into colors (name, hex_code, sort_order) values (?, '#112233', 1) returning id",
                "Color " + suffix);
        return new SeededEntity(id,
                json("name", "Color Updated " + suffix, "hexCode", "#445566", "sortOrder", 2),
                json("name", "", "hexCode", "#445566", "sortOrder", 2));
    }

    private SeededEntity seedCoupon() {
        String suffix = unique("coupon").replace("-", "").toUpperCase();
        Long id = insertForId("""
                insert into coupons (code, type, value, min_order_amount, max_discount, usage_limit,
                    used_count, start_date, end_date, status)
                values (?, 'PERCENTAGE', 10, 100000, 50000, 20, 0,
                    now() - interval '1 day', now() + interval '10 days', 'ACTIVE')
                returning id
                """, suffix);
        return new SeededEntity(id,
                json("type", "FIXED_AMOUNT", "value", 20000, "minOrderAmount", 100000, "maxDiscount", 20000,
                        "usageLimit", 10, "startDate", Instant.now().minusSeconds(3600).toString(),
                        "endDate", Instant.now().plusSeconds(86400).toString(), "status", "ACTIVE"),
                json("type", "FIXED_AMOUNT", "value", -1, "minOrderAmount", 100000, "maxDiscount", 20000,
                        "usageLimit", 10, "startDate", Instant.now().minusSeconds(3600).toString(),
                        "endDate", Instant.now().plusSeconds(86400).toString(), "status", "ACTIVE"));
    }

    private SeededEntity seedOrder() {
        Long userId = insertUserRow(unique("order-user"));
        Long id = insertOrderRow(userId, unique("order").toUpperCase(), "PENDING");
        return new SeededEntity(id,
                json("status", "CONFIRMED", "shippingFee", 15000, "discountAmount", 5000,
                        "receiverName", "Updated Receiver", "receiverPhone", "0900000001",
                        "receiverAddress", "Updated address", "paymentMethod", "COD", "paymentStatus", "UNPAID"),
                json("status", "CONFIRMED", "shippingFee", -1, "discountAmount", 5000,
                        "receiverName", "Updated Receiver", "receiverPhone", "0900000001",
                        "receiverAddress", "Updated address", "paymentMethod", "COD", "paymentStatus", "UNPAID"));
    }

    private SeededEntity seedPayment() {
        Long userId = insertUserRow(unique("payment-user"));
        Long orderId = insertOrderRow(userId, unique("payment-order").toUpperCase(), "PENDING");
        String transactionCode = unique("payment-tx").toUpperCase();
        Long id = insertForId("""
                insert into payments (order_id, provider, transaction_code, amount, status, paid_at,
                    created_at, updated_at)
                values (?, 'COD', ?, 100000, 'PENDING', null, now() - interval '1 day', now() - interval '1 day')
                returning id
                """, orderId, transactionCode);
        return new SeededEntity(id,
                json("orderId", orderId, "provider", "COD", "transactionCode", transactionCode + "-UPD",
                        "amount", 120000, "status", "SUCCESS", "paidAt", Instant.now().toString()),
                json("orderId", orderId, "provider", "COD", "transactionCode", transactionCode + "-BAD",
                        "amount", -1, "status", "SUCCESS", "paidAt", Instant.now().toString()));
    }

    private SeededEntity seedPermission() {
        String suffix = unique("permission");
        Long id = insertForId("""
                insert into permissions (name, api_path, method, module, created_at, updated_at)
                values (?, ?, 'GET', 'TEST', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, "Permission " + suffix, "/api/v1/test/" + suffix);
        return new SeededEntity(id,
                json("name", "Permission Updated " + suffix, "apiPath", "/api/v1/test/" + suffix + "/updated",
                        "method", "POST", "module", "TEST"),
                json("name", "Permission Invalid " + suffix, "apiPath", "", "method", "POST", "module", "TEST"));
    }

    private SeededEntity seedProduct() {
        Long categoryId = seedCategory().id();
        Long brandId = seedBrand().id();
        String suffix = unique("product");
        Long id = insertForId("""
                insert into products (name, slug, description, category_id, brand_id, status, created_at, updated_at)
                values (?, ?, 'Seed product', ?, ?, 'ACTIVE', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, "Product " + suffix, suffix, categoryId, brandId);
        return new SeededEntity(id,
                json("categoryId", categoryId, "brandId", brandId, "name", "Product Updated " + suffix,
                        "description", "Updated", "status", "INACTIVE"),
                json("categoryId", categoryId, "brandId", brandId, "name", "", "description", "Updated",
                        "status", "ACTIVE"));
    }

    private SeededEntity seedProductVariant() {
        Long productId = seedProduct().id();
        Long colorId = seedColor().id();
        Long sizeId = seedSize().id();
        String suffix = unique("variant").toUpperCase();
        Long id = insertForId("""
                insert into product_variants (product_id, sku, price, sale_price, stock_quantity, color_id, size_id,
                    status, created_at, updated_at)
                values (?, ?, 100000, 90000, 5, ?, ?, 'ACTIVE', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, productId, suffix, colorId, sizeId);
        return new SeededEntity(id,
                json("productId", productId, "sku", suffix + "-UPD", "price", 120000, "salePrice", 95000,
                        "stockQuantity", 7, "colorId", colorId, "sizeId", sizeId, "status", "ACTIVE"),
                json("productId", productId, "sku", "", "price", 120000, "salePrice", 95000,
                        "stockQuantity", 7, "colorId", colorId, "sizeId", sizeId, "status", "ACTIVE"));
    }

    private SeededEntity seedRole() {
        Long id = jdbcTemplate.queryForObject("select id from roles where name = 'MANAGER'", Long.class);
        return new SeededEntity(id,
                json("name", "MANAGER", "description", "Updated manager role"),
                json("name", "", "description", "Invalid role"));
    }

    private SeededEntity seedSize() {
        String suffix = unique("size");
        Long id = insertForId("insert into sizes (name, sort_order) values (?, 1) returning id", "Size " + suffix);
        return new SeededEntity(id,
                json("name", "Size Updated " + suffix, "sortOrder", 2),
                json("name", "", "sortOrder", 2));
    }

    private SeededEntity seedUser() {
        Long id = insertUserRow(unique("user"));
        return new SeededEntity(id,
                json("fullName", "Updated User", "birthDate", "1999-01-01", "avatar", null, "gender", "OTHER"),
                json("fullName", "", "birthDate", "2999-01-01", "avatar", null, "gender", "OTHER"));
    }

    private SeededEntity seedUserAddress() {
        Long userId = insertUserRow(unique("address-user"));
        Long id = insertForId("""
                insert into user_addresses (user_id, receiver_name, phone, province, district, ward,
                    address_detail, is_default)
                values (?, 'Receiver', '0900000000', 'Ho Chi Minh', 'District 1', 'Ben Nghe', '123 Test', false)
                returning id
                """, userId);
        return new SeededEntity(id,
                json("receiverName", "Updated Receiver", "phone", "0900000001", "province", "Ha Noi",
                        "district", "Cau Giay", "ward", "Dich Vong", "addressDetail", "456 Test",
                        "isDefault", false),
                json("receiverName", "", "phone", "0900000001", "province", "Ha Noi",
                        "district", "Cau Giay", "ward", "Dich Vong", "addressDetail", "456 Test",
                        "isDefault", false));
    }

    private SeededEntity seedWishlist() {
        Long userId = insertUserRow(unique("wishlist-user"));
        Long productId = seedProduct().id();
        Long id = insertForId("insert into wishlists (user_id, product_id) values (?, ?) returning id", userId, productId);
        return new SeededEntity(id, "", "");
    }

    private String noAccessToken() {
        return forbiddenToken;
    }

    private Long insertUserRow(String suffix) {
        return insertForId("""
                insert into users (full_name, email, password, birth_date, gender, created_at, updated_at)
                values (?, ?, '$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq',
                    date '1999-01-01', 'OTHER', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, "User " + suffix, suffix + "@test.local");
    }

    private Long insertOrderRow(Long userId, String orderCode, String status) {
        return insertForId("""
                insert into orders (user_id, order_code, status, subtotal, shipping_fee, discount_amount,
                    final_amount, receiver_name, receiver_phone, receiver_address, payment_method, payment_status,
                    created_at, updated_at)
                values (?, ?, ?, 100000, 10000, 0, 110000, 'Receiver', '0900000000', '123 Test',
                    'COD', 'UNPAID', now() - interval '1 day', now() - interval '1 day')
                returning id
                """, userId, orderCode, status);
    }

    private Long insertForId(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private Instant readInstant(String table, String column, Long id) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "select " + column + " from " + table + " where id = ?", Timestamp.class, id);
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private String json(Object... keyValues) {
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index < keyValues.length; index += 2) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('\"').append(keyValues[index]).append('\"').append(':');
            Object value = keyValues[index + 1];
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('\"').append(value).append('\"');
            }
        }
        return builder.append('}').toString();
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record SeededEntity(Long id, String updateBody, String invalidUpdateBody) {
    }
}

