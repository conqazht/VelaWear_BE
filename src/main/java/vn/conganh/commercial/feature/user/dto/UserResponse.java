package vn.conganh.commercial.feature.user.dto;

import java.time.Instant;
import java.util.UUID;
import vn.conganh.commercial.feature.user.User;

public record UserResponse(
        UUID id,
        String email,
        String username,
        String fullName,
        String phone,
        String avatarUrl,
        String status,
        boolean isEmailVerified,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getFullName(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
