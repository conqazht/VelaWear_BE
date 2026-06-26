package vn.conganh.commercial;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

@DisplayName("System/Security - Kiểm tra bảo vệ các API nghiệp vụ")
class SystemSecurityIntegrationTest extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // Module RBAC/User
            "/api/v1/users",
            "/api/v1/roles",
            "/api/v1/permissions",
            // Module Catalog
            "/api/v1/brands",
            "/api/v1/categories",
            "/api/v1/colors",
            "/api/v1/sizes",
            "/api/v1/products",
            "/api/v1/product-variants",
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
}
