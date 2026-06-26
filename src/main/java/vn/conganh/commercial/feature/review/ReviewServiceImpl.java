package vn.conganh.commercial.feature.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.order.OrderItemRepository;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllReviews(Pageable pageable) {
        return ResultPaginationDTO.fromPage(reviewRepository.findAll(pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByUserId(Long userId, Pageable pageable) {
        return ResultPaginationDTO.fromPage(reviewRepository.findByUserId(userId, pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderId(Long orderId, Pageable pageable) {
        return ResultPaginationDTO.fromPage(reviewRepository.findByOrderItemOrderId(orderId, pageable)
                .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getReviewsByOrderItemId(Long orderItemId, Pageable pageable) {
        return ResultPaginationDTO.fromPage(reviewRepository.findByOrderItemId(orderItemId, pageable)
                .map(this::toResponse));
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
        return toResponse(saved);
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.fromEntity(review);
    }
}
