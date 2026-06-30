package vn.conganh.commercial.feature.permission;

import org.springframework.cache.CacheManager;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionFilterRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;
import vn.conganh.commercial.feature.role.RoleRepository;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final CacheManager cacheManager;
    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllPermissions(PermissionFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(permissionRepository.findAll(Specification.where(PermissionSpecification.build(filter)), pageable)
                .map(PermissionResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(Long id) {
        return PermissionResponse.fromEntity(findPermission(id));
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByApiPathAndMethod(request.apiPath(), request.method())) {
            throw new InvalidRequestException("Permission API path and method already exists");
        }
        Permission permission = new Permission();
        permission.setName(request.name());
        permission.setApiPath(request.apiPath());
        permission.setMethod(request.method());
        permission.setModule(request.module());
        return PermissionResponse.fromEntity(permissionRepository.save(permission));
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(Long id, UpdatePermissionRequest request) {
        Permission permission = findPermission(id);
        permission.setName(request.name());
        permission.setApiPath(request.apiPath());
        permission.setMethod(request.method());
        permission.setModule(request.module());
        PermissionResponse response = PermissionResponse.fromEntity(permissionRepository.save(permission));
        clearAllRolePermissionsCache();
        return response;
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        permissionRepository.delete(findPermission(id));
        clearAllRolePermissionsCache();
    }

    private void clearAllRolePermissionsCache() {
        // Permission thay đổi có thể ảnh hưởng mọi role, nên evict toàn bộ cache role và để lần đọc sau nạp lại Redis.
        var cache = cacheManager.getCache("role_permissions");
        if (cache != null) {
            roleRepository.findAll().forEach(role -> cache.evict(role.getName()));
        }
    }

    private Permission findPermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }
}
