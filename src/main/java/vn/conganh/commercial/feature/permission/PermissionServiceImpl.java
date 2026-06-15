package vn.conganh.commercial.feature.permission;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream().map(PermissionResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(UUID id) {
        return PermissionResponse.fromEntity(findPermission(id));
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            throw new InvalidRequestException("Permission code already exists");
        }
        Permission permission = new Permission();
        permission.setCode(request.code());
        permission.setName(request.name());
        permission.setModule(request.module());
        permission.setDescription(request.description());
        return PermissionResponse.fromEntity(permissionRepository.save(permission));
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(UUID id, UpdatePermissionRequest request) {
        Permission permission = findPermission(id);
        permission.setName(request.name());
        permission.setModule(request.module());
        permission.setDescription(request.description());
        return PermissionResponse.fromEntity(permissionRepository.save(permission));
    }

    @Override
    @Transactional
    public void deletePermission(UUID id) {
        permissionRepository.delete(findPermission(id));
    }

    private Permission findPermission(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }
}
