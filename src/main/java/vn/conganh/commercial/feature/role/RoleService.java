package vn.conganh.commercial.feature.role;

import java.util.List;
import java.util.UUID;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

public interface RoleService {

    List<RoleResponse> getAllRoles();

    RoleResponse getRoleById(UUID id);

    RoleResponse createRole(CreateRoleRequest request);

    RoleResponse updateRole(UUID id, UpdateRoleRequest request);

    void deleteRole(UUID id);
}
