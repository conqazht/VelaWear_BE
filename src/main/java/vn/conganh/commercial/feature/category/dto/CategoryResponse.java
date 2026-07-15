package vn.conganh.commercial.feature.category.dto;

import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryTranslation;

public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        String slug,
        String originalSlug,
        String description,
        String seoTitle,
        String seoDescription,
        int sortOrder,
        String status,
        List<String> translationLocales,
        Instant createdAt,
        Instant updatedAt
) {

    public static CategoryResponse fromEntity(Category category) {
        return fromEntity(category, null);
    }

    public static CategoryResponse fromEntity(Category category, CategoryTranslation translation) {
        return fromEntity(category, translation, List.of());
    }

    public static CategoryResponse fromEntity(
            Category category,
            CategoryTranslation translation,
            List<String> translationLocales) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                value(translation == null ? null : translation.getName(), category.getName()),
                value(translation == null ? null : translation.getSlug(), category.getSlug()),
                category.getSlug(),
                translation == null ? null : translation.getDescription(),
                value(translation == null ? null : translation.getSeoTitle(), category.getName()),
                translation == null ? null : translation.getSeoDescription(),
                category.getSortOrder(),
                category.getStatus(),
                translationLocales,
                category.getCreatedAt(),
                category.getUpdatedAt());
    }

    private static String value(String translated, String fallback) {
        return translated == null || translated.isBlank() ? fallback : translated;
    }
}
