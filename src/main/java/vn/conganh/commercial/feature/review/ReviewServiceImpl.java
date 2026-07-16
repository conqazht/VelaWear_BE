package vn.conganh.commercial.feature.review;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.CodedBusinessException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.review.dto.ReviewSummaryResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.FilterSpecifications;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int MAX_PUBLIC_PAGE_SIZE = 100;
    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewResponseAssembler reviewResponseAssembler;
    private final ReviewImageStorage reviewImageStorage;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllReviews(ReviewFilterRequest filter, Pageable pageable) {
        return reviewResponseAssembler.toAdminPage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(filter)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByUserId(Long userId, ReviewFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("userId", userId, filter == null ? null : filter.userId());
        ReviewFilterRequest scopedFilter = filter == null
                ? emptyFilter().withUserId(userId)
                : filter.withUserId(userId);
        return findAdminReviews(scopedFilter, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getPublicReviewsByProductId(
            Long productId,
            Short rating,
            String sort,
            Pageable pageable) {
        validateRating(rating);
        if (pageable.getPageSize() > MAX_PUBLIC_PAGE_SIZE) {
            throw new InvalidRequestException("Public review page size must not exceed 100");
        }

        ReviewFilterRequest filter = new ReviewFilterRequest(
                null, productId, null, null, rating, rating, null, null, null);
        Pageable publicPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                PublicReviewSort.from(sort).toSort());
        Page<Review> reviews = reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(filter)), publicPageable);
        return reviewResponseAssembler.toPublicPage(reviews);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getProductReviewSummary(Long productId) {
        return ReviewSummaryResponse.fromCounts(reviewRepository.summarizeByProductId(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderId(Long orderId, ReviewFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("orderId", orderId, filter == null ? null : filter.orderId());
        ReviewFilterRequest scopedFilter = filter == null
                ? emptyFilter().withOrderId(orderId)
                : filter.withOrderId(orderId);
        return findAdminReviews(scopedFilter, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderItemId(
            Long orderItemId,
            ReviewFilterRequest filter,
            Pageable pageable) {
        FilterSpecifications.requireMatchingPathId(
                "orderItemId", orderItemId, filter == null ? null : filter.orderItemId());
        ReviewFilterRequest scopedFilter = filter == null
                ? emptyFilter().withOrderItemId(orderItemId)
                : filter.withOrderItemId(orderItemId);
        return findAdminReviews(scopedFilter, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyReviews(String email, Long orderId, Pageable pageable) {
        User user = findActiveUser(email);
        ReviewFilterRequest filter = new ReviewFilterRequest(
                user.getId(), null, orderId, null, null, null, null, null, null);
        return findAdminReviews(filter, pageable);
    }

    @Override
    @Transactional
    public ReviewResponse createReview(
            String email,
            CreateReviewRequest request,
            List<MultipartFile> images) {
        validateCreateRequest(request);
        User user = findActiveUser(email);
        OrderItem orderItem = findOwnedOrderItem(request.orderItemId(), user.getId());
        validateReviewEligibility(orderItem, user.getId());

        Review review = new Review();
        review.setUser(user);
        review.setOrderItem(orderItem);
        review.setRating(request.rating());
        review.setComment(normalizeComment(request.comment()));
        Review saved = saveReview(review);

        List<StoredReviewImage> storedImages = reviewImageStorage.store(images == null ? List.of() : images);
        registerRollbackCleanup(storedImages);
        try {
            saveReviewImages(saved, storedImages);
            return reviewResponseAssembler.toAdminResponse(saved);
        } catch (RuntimeException exception) {
            reviewImageStorage.delete(storedImages);
            throw exception;
        }
    }

    private ResultPaginationDTO findAdminReviews(ReviewFilterRequest filter, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(filter)), pageable);
        return reviewResponseAssembler.toAdminPage(reviews);
    }

    private User findActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private OrderItem findOwnedOrderItem(Long orderItemId, Long userId) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", orderItemId));
        if (!orderItem.getOrder().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("OrderItem", "id", orderItemId);
        }
        return orderItem;
    }

    private void validateReviewEligibility(OrderItem orderItem, Long userId) {
        if (!"COMPLETED".equalsIgnoreCase(orderItem.getOrder().getStatus())) {
            throw conflict("REVIEW_ORDER_NOT_COMPLETED", "Only completed orders can be reviewed");
        }
        if (reviewRepository.existsByUserIdAndOrderItemId(userId, orderItem.getId())) {
            throw duplicateReviewConflict();
        }
    }

    private void validateCreateRequest(CreateReviewRequest request) {
        if (request == null || request.orderItemId() == null) {
            throw new InvalidRequestException("Order item ID is required");
        }
        if (request.rating() == null) {
            throw new InvalidRequestException("Rating is required");
        }
        validateRating(request.rating());
        if (request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH) {
            throw new InvalidRequestException("Comment must not exceed 1000 characters");
        }
    }

    private void validateRating(Short rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }
    }

    private Review saveReview(Review review) {
        try {
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReviewConflict();
        }
    }

    private void saveReviewImages(Review review, List<StoredReviewImage> storedImages) {
        if (storedImages.isEmpty()) {
            return;
        }
        List<ReviewImage> imageEntities = storedImages.stream()
                .map(storedImage -> toEntity(review, storedImage))
                .toList();
        reviewImageRepository.saveAllAndFlush(imageEntities);
    }

    private ReviewImage toEntity(Review review, StoredReviewImage storedImage) {
        ReviewImage image = new ReviewImage();
        image.setReview(review);
        image.setImage(storedImage.url());
        return image;
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private void registerRollbackCleanup(List<StoredReviewImage> storedImages) {
        if (storedImages.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    reviewImageStorage.delete(storedImages);
                }
            }
        });
    }

    private CodedBusinessException duplicateReviewConflict() {
        return conflict(
                "REVIEW_ALREADY_EXISTS",
                "A review already exists for this user and order item");
    }

    private CodedBusinessException conflict(String code, String message) {
        return new CodedBusinessException(code, message, HttpStatus.CONFLICT);
    }

    private ReviewFilterRequest emptyFilter() {
        return new ReviewFilterRequest(null, null, null, null, null, null, null, null, null);
    }
}
