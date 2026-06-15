package vn.conganh.commercial.feature.permission;

import java.util.List;
import java.util.UUID;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;

public interface PermissionService {

    List<PermissionResponse> getAllPermissions();

    PermissionResponse getPermissionById(UUID id);

    PermissionResponse createPermission(CreatePermissionRequest request);

    PermissionResponse updatePermission(UUID id, UpdatePermissionRequest request);

    void deletePermission(UUID id);
}
