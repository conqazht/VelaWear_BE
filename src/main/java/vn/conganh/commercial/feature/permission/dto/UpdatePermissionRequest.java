package vn.conganh.commercial.feature.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotBlank(message = "API path is required")
        @Size(max = 255, message = "API path must be at most 255 characters")
        String apiPath,

        @NotBlank(message = "Method is required")
        @Size(max = 10, message = "Method must be at most 10 characters")
        String method,

        @NotBlank(message = "Module is required")
        @Size(max = 100, message = "Module must be at most 100 characters")
        String module
) {
}
