package vn.conganh.commercial.feature.product.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.product.ProductTranslation;

public record ProductTranslationResponse(
        String localeCode,
        String name,
        String slug,
        String shortDescription,
        String description,
        String material,
        String careInstruction,
        String seoTitle,
        String seoDescription,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductTranslationResponse fromEntity(ProductTranslation translation) {
        return new ProductTranslationResponse(
                translation.getLocaleCode(),
                translation.getName(),
                translation.getSlug(),
                translation.getShortDescription(),
                translation.getDescription(),
                translation.getMaterial(),
                translation.getCareInstruction(),
                translation.getSeoTitle(),
                translation.getSeoDescription(),
                translation.getCreatedAt(),
                translation.getUpdatedAt());
    }
}
