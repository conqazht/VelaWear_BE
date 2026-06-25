package vn.conganh.commercial.feature.review;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByUserId(Long userId) {
        return reviewRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByOrderId(Long orderId) {
        return reviewRepository.findByOrderItemOrderId(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByOrderItemId(Long orderItemId) {
        return reviewRepository.findByOrderItemId(orderItemId).stream()
                .map(this::toResponse)
                .toList();
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
