package vn.conganh.commercial.feature.review.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.review.Review;

public record ReviewResponse(
        Long id,
        Long userId,
        String userName,
        Long orderId,
        String orderCode,
        Long orderItemId,
        String productName,
        Short rating,
        String comment,
        Instant createdAt
) {

    public static ReviewResponse fromEntity(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getFullName(),
                review.getOrderItem().getOrder().getId(),
                review.getOrderItem().getOrder().getOrderCode(),
                review.getOrderItem().getId(),
                review.getOrderItem().getProductName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
