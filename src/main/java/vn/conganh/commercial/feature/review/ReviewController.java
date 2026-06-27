package vn.conganh.commercial.feature.review;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Customer review management endpoints")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviews(
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getAllReviews(filter, pageable)));
    }

    @GetMapping(path = "/user/{userId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByUser(
            @PathVariable Long userId,
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByUserId(userId, filter, pageable)));
    }

    @GetMapping(path = "/order/{orderId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrder(
            @PathVariable Long orderId,
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderId(orderId, filter, pageable)));
    }

    @GetMapping(path = "/order-item/{orderItemId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrderItem(
            @PathVariable Long orderItemId,
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderItemId(orderItemId, filter, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestBody @Valid CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reviewService.createReview(request)));
    }
}
