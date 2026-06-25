package vn.conganh.commercial.feature.role.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.role.Role;

public record RoleResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static RoleResponse fromEntity(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
