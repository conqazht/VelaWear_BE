package vn.conganh.commercial.feature.permission;

import java.util.List;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;

public interface PermissionService {

    List<PermissionResponse> getAllPermissions();

    PermissionResponse getPermissionById(Long id);

    PermissionResponse createPermission(CreatePermissionRequest request);

    PermissionResponse updatePermission(Long id, UpdatePermissionRequest request);

    void deletePermission(Long id);
}
