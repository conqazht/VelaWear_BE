package vn.conganh.commercial.feature.role;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllRoles(Pageable pageable) {
        return ResultPaginationDTO.fromPage(roleRepository.findAll(pageable)
                .map(RoleResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        return RoleResponse.fromEntity(findRole(id));
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
