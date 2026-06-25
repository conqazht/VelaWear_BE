package vn.conganh.commercial.feature.color.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateColorRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @Size(max = 7, message = "Hex code must be at most 7 characters")
        String hexCode,

        Integer sortOrder
) {
}
