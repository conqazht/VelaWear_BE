package vn.conganh.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("Customer self-service - ownership isolation")
class CustomerSelfScopeIntegrationTest extends AuthenticatedIntegrationTest {

    private static final long MISSING_ID = 999_999_999L;
    private static final String PASSWORD_HASH =
            "$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private CustomerFixture customerA;
    private CustomerFixture customerB;

    @BeforeEach
    void setUp() {
        customerA = seedCustomer("a");
        customerB = seedCustomer("b");
    }

    @Test
    @DisplayName("Self reads chỉ trả dữ liệu của principal và giữ pagination envelope")
    void selfScopedReads_returnOnlyPrincipalResources() throws Exception {
        String token = tokenFor(customerA);

        mockMvc.perform(get("/api/v1/orders/me")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.page").value(1))
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].id").value(customerA.orderId()))
                .andExpect(jsonPath("$.data.result[0].userId").value(customerA.userId()))
                .andExpect(jsonPath("$.data.result[0].orderCode").value(customerA.orderCode()))
                .andExpect(jsonPath("$.data.result[1]").doesNotExist());

        mockMvc.perform(get("/api/v1/orders/me/{id}", customerA.orderId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerA.orderId()))
                .andExpect(jsonPath("$.data.userId").value(customerA.userId()));

        mockMvc.perform(get("/api/v1/orders/me/code/{orderCode}", customerA.orderCode())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerA.orderId()))
                .andExpect(jsonPath("$.data.orderCode").value(customerA.orderCode()));

        mockMvc.perform(get("/api/v1/orders/me/{id}/status-histories", customerA.orderId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].id").value(customerA.historyId()))
                .andExpect(jsonPath("$.data.result[0].orderId").value(customerA.orderId()));

        mockMvc.perform(get("/api/v1/user-addresses/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].id").value(customerA.addressId()))
                .andExpect(jsonPath("$.data.result[0].userId").value(customerA.userId()));

        mockMvc.perform(get("/api/v1/user-addresses/me/{id}", customerA.addressId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerA.addressId()))
                .andExpect(jsonPath("$.data.userId").value(customerA.userId()));
    }

    @Test
    @DisplayName("Address mutations bind principal và giữ đúng invariant default theo từng user")
    void selfAddressMutations_bindPrincipalAndPreserveDefaultInvariant() throws Exception {
        String token = tokenFor(customerA);

        MvcResult createResult = mockMvc.perform(post("/api/v1/user-addresses/me")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressBody("New A Receiver", true, customerB.userId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(customerA.userId()))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn();

        long createdAddressId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        assertThat(addressOwner(createdAddressId)).isEqualTo(customerA.userId());
        assertThat(defaultAddressCount(customerA.userId())).isEqualTo(1);
        assertThat(isDefault(customerA.addressId())).isFalse();
        assertThat(isDefault(customerB.addressId())).isTrue();

        mockMvc.perform(put("/api/v1/user-addresses/me/{id}", customerA.addressId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateAddressBody("Updated A Receiver", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerA.addressId()))
                .andExpect(jsonPath("$.data.userId").value(customerA.userId()))
                .andExpect(jsonPath("$.data.isDefault").value(true));

        entityManager.flush();
        assertThat(defaultAddressCount(customerA.userId())).isEqualTo(1);
        assertThat(isDefault(customerA.addressId())).isTrue();
        assertThat(isDefault(createdAddressId)).isFalse();
        assertThat(isDefault(customerB.addressId())).isTrue();

        mockMvc.perform(delete("/api/v1/user-addresses/me/{id}", createdAddressId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        entityManager.flush();
        assertThat(addressExists(createdAddressId)).isFalse();
    }

    @Test
    @DisplayName("Foreign và missing identifiers cùng trả 404, không mutate tài khoản khác")
    void foreignAndMissingIdentifiers_returnNotFoundWithoutMutation() throws Exception {
        String token = tokenFor(customerA);

        expectNotFound(get("/api/v1/orders/me/{id}", customerB.orderId())
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/orders/me/code/{orderCode}", customerB.orderCode())
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/orders/me/{id}/status-histories", customerB.orderId())
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/orders/me/{id}", MISSING_ID)
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/orders/me/code/{orderCode}", "MISSING-ORDER-CODE")
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/orders/me/{id}/status-histories", MISSING_ID)
                .header("Authorization", bearer(token)));

        expectNotFound(get("/api/v1/user-addresses/me/{id}", customerB.addressId())
                .header("Authorization", bearer(token)));
        expectNotFound(put("/api/v1/user-addresses/me/{id}", customerB.addressId())
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateAddressBody("Attempted takeover", false)));
        expectNotFound(delete("/api/v1/user-addresses/me/{id}", customerB.addressId())
                .header("Authorization", bearer(token)));
        expectNotFound(get("/api/v1/user-addresses/me/{id}", MISSING_ID)
                .header("Authorization", bearer(token)));
        expectNotFound(put("/api/v1/user-addresses/me/{id}", MISSING_ID)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateAddressBody("Missing address", false)));
        expectNotFound(delete("/api/v1/user-addresses/me/{id}", MISSING_ID)
                .header("Authorization", bearer(token)));

        assertThat(addressExists(customerB.addressId())).isTrue();
        assertThat(addressReceiver(customerB.addressId())).isEqualTo("Receiver b");
        assertThat(isDefault(customerB.addressId())).isTrue();
    }

    @Test
    @DisplayName("Legacy generic reads vẫn hoạt động với production ROLE_ADMIN trong expand phase")
    void legacyGenericReads_realAdminRole_remainAvailable() throws Exception {
        jdbcTemplate.update("""
                insert into user_role (user_id, role_id)
                select ?, id from roles where name = 'ADMIN'
                on conflict do nothing
                """, customerA.userId());
        String adminToken = tokenWithRoles(
                customerA.email(), customerA.userId(), List.of("ROLE_ADMIN"));

        mockMvc.perform(get("/api/v1/orders/{id}", customerB.orderId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerB.orderId()));

        mockMvc.perform(get("/api/v1/user-addresses/{id}", customerB.addressId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(customerB.addressId()));
    }

    @Test
    @DisplayName("Self-service routes từ chối request không có access token")
    void selfServiceRoutes_withoutToken_returnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/user-addresses/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/user-addresses/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private CustomerFixture seedCustomer(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "self-" + label + "-" + suffix + "@test.local";
        Long userId = jdbcTemplate.queryForObject("""
                insert into users (full_name, email, password, birth_date, avatar, gender, security_version)
                values (?, ?, ?, date '1995-01-01', ?, 'OTHER', 0)
                returning id
                """, Long.class, "Customer " + label, email, PASSWORD_HASH, "avatar-" + label + ".png");

        jdbcTemplate.update("""
                insert into user_role (user_id, role_id)
                select ?, id from roles where name = 'USER'
                on conflict do nothing
                """, userId);

        String orderCode = ("SELF-" + label + "-" + suffix).toUpperCase();
        Long orderId = jdbcTemplate.queryForObject("""
                insert into orders (user_id, order_code, status, subtotal, shipping_fee, discount_amount,
                    final_amount, receiver_name, receiver_phone, receiver_address, payment_method, payment_status)
                values (?, ?, 'PENDING', 100000, 10000, 0, 110000, ?, '0900000000',
                    '123 Test Street', 'COD', 'UNPAID')
                returning id
                """, Long.class, userId, orderCode, "Receiver " + label);

        Long historyId = jdbcTemplate.queryForObject("""
                insert into order_status_histories (order_id, from_status, to_status, changed_by, reason)
                values (?, null, 'PENDING', ?, ?)
                returning id
                """, Long.class, orderId, userId, "Created for " + label);

        Long addressId = jdbcTemplate.queryForObject("""
                insert into user_addresses (user_id, receiver_name, phone, province, ward, address_detail, is_default)
                values (?, ?, '0900000000', 'Ho Chi Minh', 'Ben Nghe', '123 Test Street', true)
                returning id
                """, Long.class, userId, "Receiver " + label);

        return new CustomerFixture(userId, email, orderId, orderCode, historyId, addressId);
    }

    private String tokenFor(CustomerFixture customer) {
        return tokenWithRoles(customer.email(), customer.userId(), List.of("ROLE_USER"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String addressBody(String receiverName, boolean isDefault, Long attemptedUserId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "userId", attemptedUserId,
                "receiverName", receiverName,
                "phone", "0900000001",
                "province", "Ha Noi",
                "ward", "Dich Vong",
                "addressDetail", "456 Test Street",
                "isDefault", isDefault));
    }

    private String updateAddressBody(String receiverName, boolean isDefault) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "receiverName", receiverName,
                "phone", "0900000002",
                "province", "Da Nang",
                "ward", "Hai Chau",
                "addressDetail", "789 Test Street",
                "isDefault", isDefault));
    }

    private void expectNotFound(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));
    }

    private Long addressOwner(long addressId) {
        return jdbcTemplate.queryForObject(
                "select user_id from user_addresses where id = ?", Long.class, addressId);
    }

    private int defaultAddressCount(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_addresses where user_id = ? and is_default = true",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    private boolean isDefault(long addressId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_default from user_addresses where id = ?", Boolean.class, addressId));
    }

    private boolean addressExists(long addressId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_addresses where id = ?", Integer.class, addressId);
        return count != null && count == 1;
    }

    private String addressReceiver(long addressId) {
        return jdbcTemplate.queryForObject(
                "select receiver_name from user_addresses where id = ?", String.class, addressId);
    }

    private record CustomerFixture(
            Long userId,
            String email,
            Long orderId,
            String orderCode,
            Long historyId,
            Long addressId) {
    }
}
