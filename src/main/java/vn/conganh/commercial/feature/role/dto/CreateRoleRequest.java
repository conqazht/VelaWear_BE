package vn.conganh.commercial.feature.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 100, message = "Code must be at most 100 characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must be at most 150 characters")
        String name,

        String description,

        boolean isSystemRole
) {
}
