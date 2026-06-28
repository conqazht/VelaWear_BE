package vn.conganh.commercial.feature.product.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.product.Product;
import vn.conganh.commercial.feature.product.ProductTranslation;

public record ProductResponse(
        Long id,
        Long categoryId,
        Long brandId,
        String name,
        String slug,
        String originalSlug,
        String shortDescription,
        String description,
        String material,
        String careInstruction,
        String seoTitle,
        String seoDescription,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse fromEntity(Product product) {
        return fromEntity(product, null);
    }

    public static ProductResponse fromEntity(Product product, ProductTranslation translation) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getBrandId(),
                value(translation == null ? null : translation.getName(), product.getName()),
                value(translation == null ? null : translation.getSlug(), product.getSlug()),
                product.getSlug(),
                translation == null ? product.getDescription() : translation.getShortDescription(),
                value(translation == null ? null : translation.getDescription(), product.getDescription()),
                translation == null ? null : translation.getMaterial(),
                translation == null ? null : translation.getCareInstruction(),
                value(translation == null ? null : translation.getSeoTitle(), product.getName()),
                translation == null ? product.getDescription() : translation.getSeoDescription(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private static String value(String translated, String fallback) {
        return translated == null || translated.isBlank() ? fallback : translated;
    }
}
