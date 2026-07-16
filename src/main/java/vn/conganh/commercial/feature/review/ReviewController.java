package vn.conganh.commercial.feature.review;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.review.dto.CreateReviewRequest;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.feature.review.dto.ReviewResponse;
import vn.conganh.commercial.feature.review.dto.ReviewSummaryResponse;

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
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getReviewsByUserId(userId, filter, pageable)));
    }

    @GetMapping(path = "/product/{productId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Short rating,
            @RequestParam(defaultValue = "newest") String sort,
            @PageableDefault(size = 10) @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getPublicReviewsByProductId(productId, rating, sort, pageable)));
    }

    @GetMapping(path = "/product/{productId}/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getProductReviewSummary(
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getProductReviewSummary(productId)));
    }

    @GetMapping(path = "/me")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getMyReviews(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long orderId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getMyReviews(jwt.getSubject(), orderId, pageable)));
    }

    @GetMapping(path = "/order/{orderId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrder(
            @PathVariable Long orderId,
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getReviewsByOrderId(orderId, filter, pageable)));
    }

    @GetMapping(path = "/order-item/{orderItemId}")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getReviewsByOrderItem(
            @PathVariable Long orderItemId,
            @ParameterObject ReviewFilterRequest filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getReviewsByOrderItemId(orderItemId, filter, pageable)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("review") @Valid CreateReviewRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reviewService.createReview(jwt.getSubject(), request, images)));
    }
}
