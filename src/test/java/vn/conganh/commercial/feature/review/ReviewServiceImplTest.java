package vn.conganh.commercial.feature.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.review.dto.ReviewSummaryResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Review - ReviewServiceImpl")
@SuppressWarnings("unchecked")
class ReviewServiceImplTest {

    private static final String USER_EMAIL = "user1@example.com";

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewImageRepository reviewImageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ReviewResponseAssembler reviewResponseAssembler;

    @Mock
    private ReviewImageStorage reviewImageStorage;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(
                reviewRepository,
                reviewImageRepository,
                userRepository,
                orderItemRepository,
                reviewResponseAssembler,
                reviewImageStorage);
    }

    @Nested
    @DisplayName("Create review")
    class CreateReview {

        @Test
        @DisplayName("createReview - dùng principal và tạo review cho đơn đã hoàn thành")
        void createReview_validPrincipal_returnsReviewResponse() {
            User user = user(1L);
            OrderItem orderItem = orderItem(20L, order(10L, user, "COMPLETED"));
            CreateReviewRequest request = new CreateReviewRequest(20L, (short) 5, " Good product ");
            ReviewResponse expected = response(100L);
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem));
            when(reviewRepository.existsByUserIdAndOrderItemId(1L, 20L)).thenReturn(false);
            when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
                Review review = invocation.getArgument(0);
                ReflectionTestUtils.setField(review, "id", 100L);
                return review;
            });
            when(reviewImageStorage.store(anyList())).thenReturn(List.of());
            when(reviewResponseAssembler.toAdminResponse(any(Review.class))).thenReturn(expected);

            ReviewResponse response = reviewService.createReview(USER_EMAIL, request, List.of());

            assertThat(response).isEqualTo(expected);
            ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
            verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
            assertThat(reviewCaptor.getValue().getUser()).isEqualTo(user);
            assertThat(reviewCaptor.getValue().getComment()).isEqualTo("Good product");
        }

        @Test
        @DisplayName("createReview - lưu URL ảnh sau khi ghi file bằng UUID")
        void createReview_withImage_persistsReviewImage() {
            User user = user(1L);
            OrderItem orderItem = orderItem(20L, order(10L, user, "COMPLETED"));
            Review saved = review(100L, user, orderItem);
            MockMultipartFile image = new MockMultipartFile(
                    "images", "review.jpg", "image/jpeg", jpegBytes());
            StoredReviewImage storedImage = new StoredReviewImage(
                    "/uploads/reviews/uuid.jpg", Path.of("uuid.jpg"));
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem));
            when(reviewRepository.saveAndFlush(any(Review.class))).thenReturn(saved);
            when(reviewImageStorage.store(List.of(image))).thenReturn(List.of(storedImage));
            when(reviewResponseAssembler.toAdminResponse(saved)).thenReturn(response(100L));

            reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of(image));

            ArgumentCaptor<List<ReviewImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
            verify(reviewImageRepository).saveAllAndFlush(imagesCaptor.capture());
            assertThat(imagesCaptor.getValue()).singleElement().satisfies(savedImage -> {
                assertThat(savedImage.getReview()).isEqualTo(saved);
                assertThat(savedImage.getImage()).isEqualTo("/uploads/reviews/uuid.jpg");
            });
        }

        @Test
        @DisplayName("createReview - xóa file khi lưu review_images thất bại")
        void createReview_imagePersistenceFails_deletesStoredFiles() {
            User user = user(1L);
            OrderItem orderItem = orderItem(20L, order(10L, user, "COMPLETED"));
            Review saved = review(100L, user, orderItem);
            StoredReviewImage storedImage = new StoredReviewImage(
                    "/uploads/reviews/uuid.jpg", Path.of("uuid.jpg"));
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L)).thenReturn(Optional.of(orderItem));
            when(reviewRepository.saveAndFlush(any(Review.class))).thenReturn(saved);
            when(reviewImageStorage.store(anyList())).thenReturn(List.of(storedImage));
            when(reviewImageRepository.saveAllAndFlush(anyList())).thenThrow(new IllegalStateException("DB failed"));

            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of(new MockMultipartFile("images", "review.jpg", "image/jpeg", jpegBytes()))))
                    .isInstanceOf(IllegalStateException.class);
            verify(reviewImageStorage).delete(List.of(storedImage));
        }

        @Test
        @DisplayName("createReview - 404 khi order item thuộc người khác")
        void createReview_orderItemBelongsToAnotherUser_throwsNotFound() {
            User requestUser = user(1L);
            User orderUser = user(2L);
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(requestUser));
            when(orderItemRepository.findById(20L))
                    .thenReturn(Optional.of(orderItem(20L, order(10L, orderUser, "COMPLETED"))));

            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of()))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(reviewRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("createReview - 409 khi order chưa hoàn thành")
        void createReview_orderNotCompleted_throwsConflict() {
            User user = user(1L);
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L))
                    .thenReturn(Optional.of(orderItem(20L, order(10L, user, "PENDING"))));

            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of()))
                    .isInstanceOfSatisfying(CodedBusinessException.class, exception -> {
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(exception.getCode()).isEqualTo("REVIEW_ORDER_NOT_COMPLETED");
                    });
        }

        @Test
        @DisplayName("createReview - 409 khi review đã tồn tại")
        void createReview_duplicateReview_throwsConflict() {
            User user = user(1L);
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L))
                    .thenReturn(Optional.of(orderItem(20L, order(10L, user, "COMPLETED"))));
            when(reviewRepository.existsByUserIdAndOrderItemId(1L, 20L)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of()))
                    .isInstanceOfSatisfying(CodedBusinessException.class, exception ->
                            assertThat(exception.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("createReview - chuyển unique-constraint race thành 409")
        void createReview_concurrentDuplicate_throwsConflict() {
            User user = user(1L);
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(orderItemRepository.findById(20L))
                    .thenReturn(Optional.of(orderItem(20L, order(10L, user, "COMPLETED"))));
            when(reviewRepository.saveAndFlush(any(Review.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_reviews_user_order_item"));

            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "Good"),
                    List.of()))
                    .isInstanceOfSatisfying(CodedBusinessException.class, exception -> {
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(exception.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS");
                    });
        }

        @Test
        @DisplayName("createReview - 400 khi rating hoặc comment không hợp lệ")
        void createReview_invalidFields_throwsBadRequest() {
            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 6, "Good"),
                    List.of()))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> reviewService.createReview(
                    USER_EMAIL,
                    new CreateReviewRequest(20L, (short) 5, "x".repeat(1001)),
                    List.of()))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("Read reviews")
    class ReadReviews {

        @Test
        @DisplayName("getPublicReviewsByProductId - dùng sort whitelist và DTO public")
        void getPublicReviewsByProductId_validFilter_returnsPublicPage() {
            Pageable input = PageRequest.of(0, 10);
            PageImpl<Review> page = new PageImpl<>(List.of(), input, 0);
            ResultPaginationDTO expected = emptyPage();
            when(reviewRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
            when(reviewResponseAssembler.toPublicPage(page)).thenReturn(expected);

            ResultPaginationDTO result = reviewService.getPublicReviewsByProductId(
                    10L, (short) 5, "rating-high", input);

            assertThat(result).isEqualTo(expected);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findAll(any(Specification.class), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getSort()
                    .getOrderFor("rating")
                    .getDirection()
                    .isDescending()).isTrue();
        }

        @Test
        @DisplayName("getPublicReviewsByProductId - từ chối rating và sort không hợp lệ")
        void getPublicReviewsByProductId_invalidFilter_throwsBadRequest() {
            Pageable pageable = PageRequest.of(0, 10);
            assertThatThrownBy(() -> reviewService.getPublicReviewsByProductId(
                    10L, (short) 0, "newest", pageable))
                    .isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> reviewService.getPublicReviewsByProductId(
                    10L, null, "unknown", pageable))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("getProductReviewSummary - trả tổng, trung bình và đủ count 1 đến 5 sao")
        void getProductReviewSummary_existingReviews_returnsSummary() {
            when(reviewRepository.summarizeByProductId(10L)).thenReturn(List.of(
                    ratingCount((short) 5, 3L),
                    ratingCount((short) 3, 1L)));

            ReviewSummaryResponse summary = reviewService.getProductReviewSummary(10L);

            assertThat(summary.total()).isEqualTo(4);
            assertThat(summary.averageRating()).isEqualTo(4.5);
            assertThat(summary.ratingCounts()).containsEntry(1, 0L).containsEntry(5, 3L);
        }

        @Test
        @DisplayName("getMyReviews - luôn scope theo email principal và order tùy chọn")
        void getMyReviews_authenticatedPrincipal_returnsScopedPage() {
            User user = user(1L);
            Pageable pageable = PageRequest.of(0, 10);
            PageImpl<Review> page = new PageImpl<>(List.of(), pageable, 0);
            ResultPaginationDTO expected = emptyPage();
            when(userRepository.findByEmailAndDeletedAtIsNull(USER_EMAIL)).thenReturn(Optional.of(user));
            when(reviewRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
            when(reviewResponseAssembler.toAdminPage(page)).thenReturn(expected);

            assertThat(reviewService.getMyReviews(USER_EMAIL, 99L, pageable)).isEqualTo(expected);
        }
    }

    private ReviewRatingCount ratingCount(Short rating, Long total) {
        return new ReviewRatingCount() {
            @Override
            public Short getRating() {
                return rating;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private ResultPaginationDTO emptyPage() {
        return new ResultPaginationDTO(new ResultPaginationDTO.Meta(1, 10, 0, 0), List.of());
    }

    private ReviewResponse response(Long id) {
        return new ReviewResponse(
                id, 1L, "User 1", 10L, "ORD-10", 20L, "Classic Shirt", null,
                "classic-shirt", "White / M", (short) 5, "Good product", List.of(), Instant.now());
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
        orderItem.setProductSlug("classic-shirt");
        orderItem.setVariantName("White / M");
        orderItem.setSku("SKU-001");
        orderItem.setPrice(BigDecimal.valueOf(100000));
        orderItem.setQuantity(1);
        orderItem.setSubtotal(BigDecimal.valueOf(100000));
        orderItem.setStatus("CONFIRMED");
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

    private byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }
}
