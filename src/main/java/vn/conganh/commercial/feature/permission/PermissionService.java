package vn.conganh.commercial.feature.permission;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionFilterRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;

public interface PermissionService {

    ResultPaginationDTO getAllPermissions(PermissionFilterRequest filter, Pageable pageable);

    PermissionResponse getPermissionById(Long id);

    PermissionResponse createPermission(CreatePermissionRequest request);

    PermissionResponse updatePermission(Long id, UpdatePermissionRequest request);

    void deletePermission(Long id);
}
