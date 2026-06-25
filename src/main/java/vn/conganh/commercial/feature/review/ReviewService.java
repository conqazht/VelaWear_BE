package vn.conganh.commercial.feature.review;

import java.util.List;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

public interface ReviewService {

    List<ReviewResponse> getAllReviews();

    List<ReviewResponse> getReviewsByUserId(Long userId);

    List<ReviewResponse> getReviewsByOrderId(Long orderId);

    List<ReviewResponse> getReviewsByOrderItemId(Long orderItemId);

    ReviewResponse createReview(CreateReviewRequest request);
}
