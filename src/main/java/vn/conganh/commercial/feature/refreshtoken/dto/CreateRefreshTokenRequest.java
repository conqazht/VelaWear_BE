package vn.conganh.commercial.feature.refreshtoken.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateRefreshTokenRequest(

        @NotNull(message = "User ID is required")
        Long userId,

        @NotBlank(message = "Token is required")
        @Size(max = 512, message = "Token must be at most 512 characters")
        String token,

        @NotNull(message = "Expires at is required")
        @Future(message = "Expires at must be in the future")
        Instant expiresAt,

        @Size(max = 255, message = "Device info must be at most 255 characters")
        String deviceInfo,

        @Size(max = 45, message = "IP address must be at most 45 characters")
        String ipAddress
) {
}
