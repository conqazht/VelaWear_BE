package vn.conganh.commercial.feature.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotNull(message = "Brand ID is required")
        Long brandId,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 280, message = "Slug must be at most 280 characters")
        String slug,

        String description,

        @NotBlank(message = "Status is required")
        @Size(max = 20, message = "Status must be at most 20 characters")
        String status
) {
}
