package vn.conganh.commercial.feature.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBrandRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 180, message = "Slug must be at most 180 characters")
        String slug,

        String description,

        @Size(max = 20, message = "Status must be at most 20 characters")
        String status
) {
}
