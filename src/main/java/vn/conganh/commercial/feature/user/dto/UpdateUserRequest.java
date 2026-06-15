package vn.conganh.commercial.feature.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @Size(max = 32, message = "Phone must be at most 32 characters")
        String phone,

        String avatarUrl,

        @NotBlank(message = "Status is required")
        @Size(max = 30, message = "Status must be at most 30 characters")
        String status
) {
}
