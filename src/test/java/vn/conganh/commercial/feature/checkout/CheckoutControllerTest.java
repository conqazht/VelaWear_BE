package vn.conganh.commercial.feature.checkout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.conganh.commercial.feature.checkout.dto.CheckoutResponse;
import vn.conganh.commercial.security.PermissionAuthorizationManager;
import vn.conganh.commercial.security.TokenBlacklistService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CheckoutControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private PermissionAuthorizationManager permissionAuthorizationManager;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUpSecurity() {
        when(permissionAuthorizationManager.authorize(any(), any())).thenReturn(new AuthorizationDecision(true));
        when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistService.getRoleUpdateTimestamp(anyLong())).thenReturn(null);
    }

    // Task 5.7
    @Test
    @DisplayName("Should return 201 Created on valid checkout request")
    void checkout_validRequest_returns201() throws Exception {
        CheckoutResponse mockResponse = new CheckoutResponse(
                1L,
                "VELA-12345678",
                "PENDING",
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(15),
                BigDecimal.ZERO,
                BigDecimal.valueOf(115),
                "Receiver",
                "0123456789",
                "Address",
                "COD",
                "UNPAID",
                List.of(),
                null,
                Instant.now()
        );

        when(checkoutService.checkout(any(), eq("test@example.com"), eq("checkout-test-key"), eq("vi")))
                .thenReturn(mockResponse);

        String requestBody = """
                {
                    "receiverName": "Receiver",
                    "receiverPhone": "0123456789",
                    "receiverAddress": "Address",
                    "paymentMethod": "COD",
                    "shippingFee": 15,
                    "pricingFingerprint": "preview-fingerprint"
                }
                """;

        mockMvc.perform(post("/api/v1/checkout")
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com")))
                        .header("Idempotency-Key", "checkout-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.orderCode").value("VELA-12345678"))
                .andExpect(jsonPath("$.data.finalAmount").value(115));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when validation fails (missing receiver name)")
    void checkout_missingReceiverName_returns400() throws Exception {
        String requestBody = """
                {
                    "receiverName": "",
                    "receiverPhone": "0123456789",
                    "receiverAddress": "Address",
                    "paymentMethod": "COD",
                    "shippingFee": 15,
                    "pricingFingerprint": "preview-fingerprint"
                }
                """;

        mockMvc.perform(post("/api/v1/checkout")
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com")))
                        .header("Idempotency-Key", "validation-test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.data.receiverName").value("Receiver name is required"));
    }

    @Test
    @DisplayName("Should return stable 400 when Idempotency-Key is missing")
    void checkout_missingIdempotencyKey_returns400() throws Exception {
        String requestBody = """
                {
                    "receiverName": "Receiver",
                    "receiverPhone": "0123456789",
                    "receiverAddress": "Address",
                    "paymentMethod": "COD",
                    "pricingFingerprint": "preview-fingerprint"
                }
                """;

        mockMvc.perform(post("/api/v1/checkout")
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"))
                .andExpect(jsonPath("$.data.header").value("Idempotency-Key"));
    }

    @Test
    @DisplayName("Should return 200 OK on valid cancel request")
    void cancelOrder_validRequest_returns200() throws Exception {
        doNothing().when(checkoutService).cancelOrder(1L, "test@example.com");

        mockMvc.perform(post("/api/v1/checkout/{orderId}/cancel", 1L)
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));
    }
}
