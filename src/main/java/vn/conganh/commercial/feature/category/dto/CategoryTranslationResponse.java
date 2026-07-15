package vn.conganh.commercial.feature.category.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.category.CategoryTranslation;

public record CategoryTranslationResponse(
        String localeCode,
        String name,
        String slug,
        String description,
        String seoTitle,
        String seoDescription,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryTranslationResponse fromEntity(CategoryTranslation translation) {
        return new CategoryTranslationResponse(
                translation.getLocaleCode(),
                translation.getName(),
                translation.getSlug(),
                translation.getDescription(),
                translation.getSeoTitle(),
                translation.getSeoDescription(),
                translation.getCreatedAt(),
                translation.getUpdatedAt());
    }
}
