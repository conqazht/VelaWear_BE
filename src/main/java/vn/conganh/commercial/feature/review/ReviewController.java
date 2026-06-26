package vn.conganh.commercial.feature.review;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getAllReviews()));
    }

    @GetMapping(path = "/user/{userId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByUserId(userId)));
    }

    @GetMapping(path = "/order/{orderId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderId(orderId)));
    }

    @GetMapping(path = "/order-item/{orderItemId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByOrderItem(@PathVariable Long orderItemId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewsByOrderItemId(orderItemId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestBody @Valid CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reviewService.createReview(request)));
    }
}
