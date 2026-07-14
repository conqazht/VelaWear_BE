package vn.conganh.commercial.feature.review;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.FilterSpecifications;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllReviews(ReviewFilterRequest filter, Pageable pageable) {
        return toResponsePage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(filter)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByUserId(Long userId, ReviewFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("userId", userId, filter == null ? null : filter.userId());
        ReviewFilterRequest scopedFilter = filter == null
                ? new ReviewFilterRequest(userId, null, null, null, null, null, null, null, null)
                : filter.withUserId(userId);

        return toResponsePage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(scopedFilter)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByProductId(
            Long productId,
            ReviewFilterRequest filter,
            Pageable pageable) {
        FilterSpecifications.requireMatchingPathId(
                "productId", productId, filter == null ? null : filter.productId());
        ReviewFilterRequest scopedFilter = filter == null
                ? new ReviewFilterRequest(null, productId, null, null, null, null, null, null, null)
                : filter.withProductId(productId);

        return toResponsePage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(scopedFilter)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderId(Long orderId, ReviewFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("orderId", orderId, filter == null ? null : filter.orderId());
        ReviewFilterRequest scopedFilter = filter == null
                ? new ReviewFilterRequest(null, null, orderId, null, null, null, null, null, null)
                : filter.withOrderId(orderId);

        return toResponsePage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(scopedFilter)), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderItemId(Long orderItemId, ReviewFilterRequest filter, Pageable pageable) {
        FilterSpecifications.requireMatchingPathId("orderItemId", orderItemId, filter == null ? null : filter.orderItemId());
        ReviewFilterRequest scopedFilter = filter == null
                ? new ReviewFilterRequest(null, null, null, orderItemId, null, null, null, null, null)
                : filter.withOrderItemId(orderItemId);

        return toResponsePage(reviewRepository.findAll(
                Specification.where(ReviewSpecification.build(scopedFilter)), pageable));
    }

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.userId()));

        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", request.orderItemId()));

        if (!orderItem.getOrder().getUser().getId().equals(user.getId())) {
            throw new InvalidRequestException("Order item does not belong to this user");
        }

        if (!"COMPLETED".equalsIgnoreCase(orderItem.getOrder().getStatus())) {
            throw new InvalidRequestException("Only completed orders can be reviewed");
        }

        if (request.rating() < 1 || request.rating() > 5) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }

        if (reviewRepository.existsByUserIdAndOrderItemId(request.userId(), request.orderItemId())) {
            throw new InvalidRequestException("A review already exists for this user and order item");
        }

        Review review = new Review();
        review.setUser(user);
        review.setOrderItem(orderItem);
        review.setRating(request.rating());
        review.setComment(request.comment());

        Review saved = reviewRepository.save(review);
        ProductVariant variant = saved.getOrderItem().getVariantId() == null
                ? null
                : productVariantRepository.findByIdAndDeletedAtIsNull(saved.getOrderItem().getVariantId()).orElse(null);
        return ReviewResponse.fromEntity(saved, variant);
    }

    private ResultPaginationDTO toResponsePage(Page<Review> reviews) {
        List<Long> variantIds = reviews.getContent().stream()
                .map(Review::getOrderItem)
                .map(OrderItem::getVariantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductVariant> variantsById = variantIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findAllByIdInAndDeletedAtIsNull(variantIds).stream()
                        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        return ResultPaginationDTO.fromPage(reviews.map(review -> {
            Long variantId = review.getOrderItem().getVariantId();
            return ReviewResponse.fromEntity(
                    review,
                    variantId == null ? null : variantsById.get(variantId));
        }));
    }
}
