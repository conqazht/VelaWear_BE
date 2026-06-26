package vn.conganh.commercial.feature.review;

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
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Review - ReviewController")
class ReviewControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /reviews - 201: tạo review thành công cho item của order đã hoàn thành")
        void createReview_validRequest_returnsCreatedReview() throws Exception {
            // Arrange
            User user = userRepository.save(user("review.user@velawear.local"));
            Order order = orderRepository.save(order(user, "ORD-REVIEW-POST", "COMPLETED"));
            OrderItem orderItem = orderItemRepository.save(orderItem(order));
            CreateReviewRequest request = new CreateReviewRequest(user.getId(), orderItem.getId(), (short) 5, "Good");

            // Act & Assert
            mockMvc.perform(post("/api/v1/reviews")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.orderItemId").value(orderItem.getId()))
                    .andExpect(jsonPath("$.data.productName", is("Classic Shirt")))
                    .andExpect(jsonPath("$.data.rating").value(5));

            assertThat(reviewRepository.existsByUserIdAndOrderItemId(user.getId(), orderItem.getId())).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /reviews - 400: từ chối khi rating lớn hơn 5")
        void createReview_ratingGreaterThanFive_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            User user = userRepository.save(user("review.validation@velawear.local"));
            Order order = orderRepository.save(order(user, "ORD-REVIEW-VALIDATION", "COMPLETED"));
            OrderItem orderItem = orderItemRepository.save(orderItem(order));
            CreateReviewRequest request = new CreateReviewRequest(user.getId(), orderItem.getId(), (short) 6, "Bad rating");

            // Act & Assert
            mockMvc.perform(post("/api/v1/reviews")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(reviewRepository.existsByUserIdAndOrderItemId(user.getId(), orderItem.getId())).isFalse();
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /reviews - 400: từ chối khi order chưa hoàn thành")
        void createReview_orderNotCompleted_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            User user = userRepository.save(user("review.pending@velawear.local"));
            Order order = orderRepository.save(order(user, "ORD-REVIEW-PENDING", "PENDING"));
            OrderItem orderItem = orderItemRepository.save(orderItem(order));
            CreateReviewRequest request = new CreateReviewRequest(user.getId(), orderItem.getId(), (short) 5, "Good");

            // Act & Assert
            mockMvc.perform(post("/api/v1/reviews")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(reviewRepository.existsByUserIdAndOrderItemId(user.getId(), orderItem.getId())).isFalse();
        }
    }

    private OrderItem orderItem(Order order) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductName("Classic Shirt");
        orderItem.setVariantName("White / M");
        orderItem.setSku("SKU-REVIEW-001");
        orderItem.setPrice(BigDecimal.valueOf(100000));
        orderItem.setQuantity(1);
        orderItem.setSubtotal(BigDecimal.valueOf(100000));
        orderItem.setStatus("CONFIRMED");
        return orderItem;
    }

    private Order order(User user, String orderCode, String status) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(orderCode);
        order.setStatus(status);
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(100000));
        order.setReceiverName("Nguyen Van A");
        order.setReceiverPhone("0123456789");
        order.setReceiverAddress("123 Le Loi");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("PAID");
        return order;
    }

    private User user(String email) {
        User user = new User();
        user.setFullName("Review Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$alreadyencoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
