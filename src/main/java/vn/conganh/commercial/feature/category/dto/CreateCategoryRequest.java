package vn.conganh.commercial.feature.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCategoryRequest(
        UUID parentId,

        @NotBlank(message = "Name is required")
        @Size(max = 180, message = "Name must be at most 180 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 220, message = "Slug must be at most 220 characters")
        String slug,

        String description,

        int sortOrder,

        boolean isActive
) {
}
