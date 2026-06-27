package vn.conganh.commercial.feature.permission;

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

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

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
        return PermissionResponse.fromEntity(permissionRepository.save(permission));
    }

    @Override
    @Transactional
    public void deletePermission(Long id) {
        permissionRepository.delete(findPermission(id));
    }

    private Permission findPermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }
}
