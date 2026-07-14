package vn.conganh.commercial.feature.review.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.review.Review;

public record ReviewResponse(
        Long id,
        Long userId,
        String userName,
        Long orderId,
        String orderCode,
        Long orderItemId,
        String productName,
        Long productId,
        String productSlug,
        Short rating,
        String comment,
        Instant createdAt
) {

    public static ReviewResponse fromEntity(Review review, ProductVariant variant) {
        Product product = variant == null ? null : variant.getProduct();

        return new ReviewResponse(
                review.getId(),
                review.getUser().getId(),
                review.getUser().getFullName(),
                review.getOrderItem().getOrder().getId(),
                review.getOrderItem().getOrder().getOrderCode(),
                review.getOrderItem().getId(),
                review.getOrderItem().getProductName(),
                product == null ? null : product.getId(),
                product == null ? null : product.getSlug(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt());
    }
}
