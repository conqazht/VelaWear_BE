package vn.conganh.commercial.feature.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import vn.conganh.commercial.feature.product.Product;

public record ProductResponse(
        UUID id,
        UUID categoryId,
        String sku,
        String name,
        String slug,
        String description,
        String status,
        BigDecimal basePrice,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getSku(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getStatus(),
                product.getBasePrice(),
                product.getCurrency(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
