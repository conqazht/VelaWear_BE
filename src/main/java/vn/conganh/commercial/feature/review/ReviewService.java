package vn.conganh.commercial.feature.review;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.review.dto.ReviewSummaryResponse;

public interface ReviewService {

    ResultPaginationDTO getAllReviews(ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByUserId(Long userId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getPublicReviewsByProductId(
            Long productId,
            Short rating,
            String sort,
            Pageable pageable);

    ReviewSummaryResponse getProductReviewSummary(Long productId);

    ResultPaginationDTO getReviewsByOrderId(Long orderId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getReviewsByOrderItemId(Long orderItemId, ReviewFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getMyReviews(String email, Long orderId, Pageable pageable);

    ReviewResponse createReview(
            String email,
            CreateReviewRequest request,
            List<MultipartFile> images);
}
