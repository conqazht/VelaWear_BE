package vn.conganh.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

@DisplayName("System/Security - Kiểm tra bảo vệ các API nghiệp vụ")
class SystemSecurityIntegrationTest extends AuthenticatedIntegrationTest {

    @Test
    @DisplayName("Actuator metrics chỉ cho ADMIN")
    void actuatorMetrics_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info"
    })
    @DisplayName("Actuator health, probes và info đều public")
    void actuatorHealthAndInfo_arePublic(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Correlation ID luôn do server sinh và không tin header client")
    void correlationId_isServerGenerated() throws Exception {
        String requestId = mockMvc.perform(get("/actuator/health")
                        .header("X-Request-ID", "forged-client-id"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-ID");

        assertThat(requestId).isNotEqualTo("forged-client-id");
        assertThat(requestId).matches("[0-9a-f-]{36}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Module RBAC/User
            "/api/v1/users",
            "/api/v1/roles",
            "/api/v1/permissions",
            // Module Catalog
            "/api/v1/colors",
            "/api/v1/sizes",
            // Module Cart/Coupon/Order/Payment/Review
            "/api/v1/carts",
            "/api/v1/coupons",
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/reviews",
            // Module User Address/Wishlist
            "/api/v1/user-addresses",
            "/api/v1/wishlists"
    })
    @DisplayName("GET: từ chối người dùng chưa đăng nhập khi truy cập API được bảo vệ")
    void protectedCrudEndpoints_anonymousGet_returnsUnauthorized(String path) throws Exception {
        // Act & Assert
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Module User
            "/api/v1/users",
            // Module Catalog
            "/api/v1/brands",
            "/api/v1/categories",
            "/api/v1/colors",
            "/api/v1/sizes",
            "/api/v1/products",
            // Module Coupon/Order/Payment/Review
            "/api/v1/coupons",
            "/api/v1/orders",
            "/api/v1/payments",
            "/api/v1/reviews"
    })
    @DisplayName("POST: từ chối người dùng chưa đăng nhập khi tạo dữ liệu ở các module")
    void protectedCrudEndpoints_anonymousPost_returnsUnauthorized(String path) throws Exception {
        // Act & Assert
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Module User
            "/api/v1/users/1",
            // Module Catalog
            "/api/v1/brands/1",
            "/api/v1/categories/1",
            "/api/v1/colors/1",
            "/api/v1/sizes/1",
            "/api/v1/products/1",
            // Module Coupon/Order/Payment
            "/api/v1/coupons/1",
            "/api/v1/orders/1",
            "/api/v1/payments/1"
    })
    @DisplayName("PUT/DELETE: từ chối người dùng chưa đăng nhập khi cập nhật hoặc xóa dữ liệu")
    void protectedCrudEndpoints_anonymousMutation_returnsUnauthorized(String path) throws Exception {
        // Act & Assert
        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users",
            "/api/v1/roles",
            "/api/v1/permissions"
    })
    @DisplayName("GET: trả về 403 khi user đã đăng nhập nhưng không có quyền admin/RBAC")
    void adminEndpoints_userRoleGet_returnsForbidden(String path) throws Exception {
        // Act & Assert
        mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/users",
            "/api/v1/roles",
            "/api/v1/permissions",
            "/api/v1/coupons"
    })
    @DisplayName("POST: trả về 403 khi user đã đăng nhập nhưng không có quyền tạo dữ liệu admin")
    void adminEndpoints_userRolePost_returnsForbidden(String path) throws Exception {
        // Act & Assert
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
