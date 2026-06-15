package vn.conganh.commercial.feature.permission.dto;

import java.time.Instant;
import java.util.UUID;
import vn.conganh.commercial.feature.permission.Permission;

public record PermissionResponse(
        UUID id,
        String code,
        String name,
        String module,
        String description,
        Instant createdAt
) {

    public static PermissionResponse fromEntity(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getModule(),
                permission.getDescription(),
                permission.getCreatedAt());
    }
}
