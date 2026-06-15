package vn.conganh.commercial.feature.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 150, message = "Code must be at most 150 characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        @NotBlank(message = "Module is required")
        @Size(max = 80, message = "Module must be at most 80 characters")
        String module,

        String description
) {
}
