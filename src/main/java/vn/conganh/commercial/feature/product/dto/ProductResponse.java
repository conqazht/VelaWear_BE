package vn.conganh.commercial.feature.product.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.product.Product;

public record ProductResponse(
        Long id,
        Long categoryId,
        Long brandId,
        String name,
        String slug,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getBrandId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
