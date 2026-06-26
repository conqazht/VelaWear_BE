package vn.conganh.commercial.feature.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.order.dto.CreateOrderRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Order - OrderController")
class OrderControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /orders - 201: tạo order thành công khi dữ liệu hợp lệ")
        void createOrder_validRequest_returnsCreatedOrder() throws Exception {
            // Arrange
            User user = userRepository.save(user("order.user@velawear.local"));
            CreateOrderRequest request = validRequest(user.getId(), "ORD-POST-001");

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.orderCode", is("ORD-POST-001")))
                    .andExpect(jsonPath("$.data.status", is("PENDING")));

            assertThat(orderRepository.findByOrderCode("ORD-POST-001")).isPresent();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /orders - 400: từ chối khi orderCode để trống")
        void createOrder_blankOrderCode_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            User user = userRepository.save(user("order.blank@velawear.local"));
            CreateOrderRequest request = validRequest(user.getId(), "");

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(orderRepository.findAll()).noneMatch(order -> order.getUser().getId().equals(user.getId()));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /orders - 400: từ chối khi orderCode đã tồn tại")
        void createOrder_duplicateOrderCode_returnsBadRequestAndDoesNotCreateNewOrder() throws Exception {
            // Arrange
            User user = userRepository.save(user("order.duplicate@velawear.local"));
            orderRepository.save(order(user, "ORD-DUPLICATE-POST"));
            CreateOrderRequest request = validRequest(user.getId(), "ORD-DUPLICATE-POST");
            long countBefore = orderRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(orderRepository.count()).isEqualTo(countBefore);
        }
    }

    private CreateOrderRequest validRequest(Long userId, String orderCode) {
        return new CreateOrderRequest(
                userId,
                orderCode,
                "PENDING",
                BigDecimal.valueOf(100000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(100000),
                "Nguyen Van A",
                "0123456789",
                "123 Le Loi",
                "COD",
                "UNPAID");
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
        user.setFullName("Order Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
