package vn.conganh.commercial.feature.review;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

public interface ReviewService {

    ResultPaginationDTO getAllReviews(Pageable pageable);

    ResultPaginationDTO getReviewsByUserId(Long userId, Pageable pageable);

    ResultPaginationDTO getReviewsByOrderId(Long orderId, Pageable pageable);

    ResultPaginationDTO getReviewsByOrderItemId(Long orderItemId, Pageable pageable);

    ReviewResponse createReview(CreateReviewRequest request);
}
