package vn.conganh.commercial.feature.permission.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.permission.Permission;

public record PermissionResponse(
        Long id,
        String name,
        String apiPath,
        String method,
        String module,
        Instant createdAt,
        Instant updatedAt
) {

    public static PermissionResponse fromEntity(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getName(),
                permission.getApiPath(),
                permission.getMethod(),
                permission.getModule(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}
