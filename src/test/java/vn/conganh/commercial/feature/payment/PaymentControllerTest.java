package vn.conganh.commercial.feature.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Payment - PaymentController")
class PaymentControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /payments - 201: tạo payment thành công khi order tồn tại")
        void createPayment_validRequest_returnsCreatedPayment() throws Exception {
            // Arrange
            User user = userRepository.save(user("payment.user@velawear.local"));
            Order order = orderRepository.save(order(user, "ORD-PAYMENT-POST"));
            CreatePaymentRequest request = validRequest(order.getId(), "PAYMENT-TX-POST");

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.orderId").value(order.getId()))
                    .andExpect(jsonPath("$.data.provider", is("COD")))
                    .andExpect(jsonPath("$.data.status", is("SUCCESS")));

            assertThat(paymentRepository.findAll()).anyMatch(payment ->
                    payment.getTransactionCode().equals("PAYMENT-TX-POST"));
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /payments - 400: từ chối khi amount null")
        void createPayment_nullAmount_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            User user = userRepository.save(user("payment.validation@velawear.local"));
            Order order = orderRepository.save(order(user, "ORD-PAYMENT-VALIDATION"));
            CreatePaymentRequest request = new CreatePaymentRequest(
                    order.getId(),
                    PaymentProvider.COD,
                    "PAYMENT-TX-VALIDATION",
                    null,
                    PaymentStatus.SUCCESS,
                    Instant.parse("2026-01-01T00:00:00Z"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(paymentRepository.findAll()).noneMatch(payment ->
                    "PAYMENT-TX-VALIDATION".equals(payment.getTransactionCode()));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /payments - 404: từ chối khi order không tồn tại")
        void createPayment_missingOrder_returnsNotFoundAndDoesNotSave() throws Exception {
            // Arrange
            CreatePaymentRequest request = validRequest(999_999L, "PAYMENT-TX-MISSING-ORDER");
            long countBefore = paymentRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/payments")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.statusCode").value(404));

            assertThat(paymentRepository.count()).isEqualTo(countBefore);
        }
    }

    private CreatePaymentRequest validRequest(Long orderId, String transactionCode) {
        return new CreatePaymentRequest(
                orderId,
                PaymentProvider.COD,
                transactionCode,
                BigDecimal.valueOf(100000),
                PaymentStatus.SUCCESS,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private Order order(User user, String orderCode) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(orderCode);
        order.setStatus("PENDING");
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(100000));
        order.setReceiverName("Nguyen Van A");
        order.setReceiverPhone("0123456789");
        order.setReceiverAddress("123 Le Loi");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("UNPAID");
        return order;
    }

    private User user(String email) {
        User user = new User();
        user.setFullName("Payment Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
