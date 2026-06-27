package vn.conganh.commercial.feature.role.dto;

import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.role.Role;

public record RoleResponse(
        Long id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<PermissionResponse> permissions
) {

    public static RoleResponse fromEntity(Role role) {
        return fromEntity(role, List.of());
    }

    public static RoleResponse fromEntity(Role role, List<PermissionResponse> permissions) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                permissions);
    }
}
