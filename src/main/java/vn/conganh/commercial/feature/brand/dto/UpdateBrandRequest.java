package vn.conganh.commercial.feature.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBrandRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        String description,

        @NotBlank(message = "Status is required")
        @Size(max = 20, message = "Status must be at most 20 characters")
        String status
) {
}
