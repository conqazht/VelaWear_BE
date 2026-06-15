package vn.conganh.commercial.feature.role.dto;

import java.time.Instant;
import java.util.UUID;
import vn.conganh.commercial.feature.role.Role;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean isSystemRole,
        Instant createdAt,
        Instant updatedAt
) {

    public static RoleResponse fromEntity(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
