package vn.conganh.commercial.feature.review.dto;

import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.review.Review;

public record PublicReviewResponse(
        Long id,
        String userName,
        Long productId,
        String productName,
        String productSlug,
        String variantName,
        Short rating,
        String comment,
        List<String> images,
        boolean verifiedPurchase,
        Instant createdAt
) {

    public static PublicReviewResponse fromEntity(
            Review review,
            ProductVariant variant,
            List<String> images) {
        Product product = variant == null ? null : variant.getProduct();

        return new PublicReviewResponse(
                review.getId(),
                review.getUser().getFullName(),
                product == null ? null : product.getId(),
                review.getOrderItem().getProductName(),
                product == null ? review.getOrderItem().getProductSlug() : product.getSlug(),
                review.getOrderItem().getVariantName(),
                review.getRating(),
                review.getComment(),
                images == null ? List.of() : List.copyOf(images),
                "COMPLETED".equalsIgnoreCase(review.getOrderItem().getOrder().getStatus()),
                review.getCreatedAt());
    }
}
