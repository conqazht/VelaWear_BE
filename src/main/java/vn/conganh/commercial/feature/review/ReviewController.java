package vn.conganh.commercial.feature.review;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviews(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getAllReviews(pageable)));
    }

    @GetMapping(path = "/user/{userId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByUser(
            @PathVariable Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByUserId(userId, pageable)));
    }

    @GetMapping(path = "/order/{orderId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrder(
            @PathVariable Long orderId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderId(orderId, pageable)));
    }

    @GetMapping(path = "/order-item/{orderItemId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrderItem(
            @PathVariable Long orderItemId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderItemId(orderItemId, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestBody @Valid CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reviewService.createReview(request)));
    }
}
