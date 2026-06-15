package vn.conganh.commercial.feature.category.dto;

import java.time.Instant;
import java.util.UUID;
import vn.conganh.commercial.feature.category.Category;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String description,
        int sortOrder,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {

    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getSortOrder(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
