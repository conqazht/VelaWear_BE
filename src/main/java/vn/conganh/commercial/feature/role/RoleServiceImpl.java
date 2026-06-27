package vn.conganh.commercial.feature.role;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleFilterRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllRoles(RoleFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(roleRepository.findAll(Specification.where(RoleSpecification.build(filter)), pageable)
                .map(RoleResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = findRole(id);
        List<PermissionResponse> permissions = roleRepository.findPermissionsByRoleId(role.getId()).stream()
                .map(PermissionResponse::fromEntity)
                .toList();
        return RoleResponse.fromEntity(role, permissions);
    }

    @Override
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new InvalidRequestException("Role name already exists");
        }
        Role role = new Role();
        role.setName(request.name());
        role.setDescription(request.description());
        return RoleResponse.fromEntity(roleRepository.save(role));
    }

    @Override
    @Transactional
    public RoleResponse updateRole(Long id, UpdateRoleRequest request) {
        Role role = findRole(id);
        role.setName(request.name());
        role.setDescription(request.description());
        return RoleResponse.fromEntity(roleRepository.save(role));
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        roleRepository.delete(findRole(id));
    }

    private Role findRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }
}
