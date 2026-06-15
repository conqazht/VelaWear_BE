package vn.conganh.commercial.feature.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateCategoryRequest(
        UUID parentId,

        @NotBlank(message = "Name is required")
        @Size(max = 180, message = "Name must be at most 180 characters")
        String name,

        String description,

        int sortOrder,

        boolean isActive
) {
}
