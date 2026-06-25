package vn.conganh.commercial.feature.category.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.category.Category;

public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        String slug,
        int sortOrder,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getSlug(),
                category.getSortOrder(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
