package vn.conganh.commercial.feature.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Review - ReviewServiceImpl")
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewRepository, userRepository, orderItemRepository);
    }

    @Nested
    @DisplayName("Create review")
    class CreateReview {

        @Test
        @DisplayName("createReview - tạo review thành công cho item thuộc order đã hoàn thành")
        void createReview_validRequest_returnsReviewResponse() {
            // Arrange
            User user = user(1L);
            OrderItem orderItem = orderItem(20L, order(10L, user, "COMPLETED"));
            CreateReviewRequest request = new CreateReviewRequest(1L, 20L, (short) 5, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem));
            when(reviewRepository.existsByUserIdAndOrderItemId(1L, 20L)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
                Review review = invocation.getArgument(0);
                ReflectionTestUtils.setField(review, "id", 100L);
                return review;
            });

            // Act
            ReviewResponse response = reviewService.createReview(request);

            // Assert
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.orderItemId()).isEqualTo(20L);
            assertThat(response.productName()).isEqualTo("Classic Shirt");
            assertThat(response.rating()).isEqualTo((short) 5);
            verify(reviewRepository).save(argThat(review ->
                    review.getUser().equals(user) && review.getOrderItem().equals(orderItem)));
        }

        @Test
        @DisplayName("createReview - không gọi save khi order item không thuộc user")
        void createReview_orderItemBelongsToAnotherUser_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            User requestUser = user(1L);
            User orderUser = user(2L);
            CreateReviewRequest request = new CreateReviewRequest(1L, 20L, (short) 5, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(requestUser));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem(20L, order(10L, orderUser, "COMPLETED"))));

            // Act & Assert
            assertThatThrownBy(() -> reviewService.createReview(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("does not belong");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("createReview - không gọi save khi order chưa hoàn thành")
        void createReview_orderNotCompleted_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            User user = user(1L);
            CreateReviewRequest request = new CreateReviewRequest(1L, 20L, (short) 5, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem(20L, order(10L, user, "PENDING"))));

            // Act & Assert
            assertThatThrownBy(() -> reviewService.createReview(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("completed");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("createReview - không gọi save khi rating ngoài khoảng 1 đến 5")
        void createReview_invalidRating_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            User user = user(1L);
            CreateReviewRequest request = new CreateReviewRequest(1L, 20L, (short) 6, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem(20L, order(10L, user, "COMPLETED"))));

            // Act & Assert
            assertThatThrownBy(() -> reviewService.createReview(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("between 1 and 5");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("createReview - không gọi save khi review cho order item đã tồn tại")
        void createReview_duplicateOrderItemReview_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            User user = user(1L);
            CreateReviewRequest request = new CreateReviewRequest(1L, 20L, (short) 5, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem(20L, order(10L, user, "COMPLETED"))));
            when(reviewRepository.existsByUserIdAndOrderItemId(1L, 20L)).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> reviewService.createReview(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already exists");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("createReview - ném ResourceNotFoundException khi order item không tồn tại")
        void createReview_missingOrderItem_throwsResourceNotFoundException() {
            // Arrange
            CreateReviewRequest request = new CreateReviewRequest(1L, 99L, (short) 5, "Good product");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
            when(orderItemRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> reviewService.createReview(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Read review")
    class ReadReview {

        @Test
        @DisplayName("getReviewsByOrderId - trả về review theo order")
        void getReviewsByOrderId_existingReviews_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            User user = user(1L);
            when(reviewRepository.findAll(ArgumentMatchers.<Specification<Review>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(
                            List.of(review(100L, user, orderItem(20L, order(10L, user, "COMPLETED")))),
                            pageable,
                            1));

            // Act
            ResultPaginationDTO responses = reviewService.getReviewsByOrderId(10L, null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("orderId").containsExactly(10L);
            assertThat(responses.result()).extracting("orderItemId").containsExactly(20L);
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getReviewsByOrderItemId - trả về review theo order item")
        void getReviewsByOrderItemId_existingReviews_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            User user = user(1L);
            when(reviewRepository.findAll(ArgumentMatchers.<Specification<Review>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(
                            List.of(review(100L, user, orderItem(20L, order(10L, user, "COMPLETED")))),
                            pageable,
                            1));

            // Act
            ResultPaginationDTO responses = reviewService.getReviewsByOrderItemId(20L, null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("productName").containsExactly("Classic Shirt");
            assertThat(responses.meta().page()).isEqualTo(1);
        }
    }

    private Review review(Long id, User user, OrderItem orderItem) {
        Review review = new Review();
        ReflectionTestUtils.setField(review, "id", id);
        review.setUser(user);
        review.setOrderItem(orderItem);
        review.setRating((short) 5);
        review.setComment("Good product");
        return review;
    }

    private OrderItem orderItem(Long id, Order order) {
        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "id", id);
        orderItem.setOrder(order);
        orderItem.setProductName("Classic Shirt");
        orderItem.setVariantName("White / M");
        orderItem.setSku("SKU-001");
        orderItem.setPrice(BigDecimal.valueOf(100000));
        orderItem.setQuantity(1);
        orderItem.setSubtotal(BigDecimal.valueOf(100000));
        orderItem.setStatus("FULFILLED");
        return orderItem;
    }

    private Order order(Long id, User user, String status) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setUser(user);
        order.setOrderCode("ORD-" + id);
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

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }
}
