package vn.conganh.commercial.feature.review;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

public interface ReviewService {

    ResultPaginationDTO getAllReviews(ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByUserId(Long userId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByProductId(Long productId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByOrderId(Long orderId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByOrderItemId(Long orderItemId, ReviewFilterRequest filter, Pageable pageable);

    ReviewResponse createReview(CreateReviewRequest request);
}
