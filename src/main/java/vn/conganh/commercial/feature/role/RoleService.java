package vn.conganh.commercial.feature.role;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleFilterRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

public interface RoleService {

    ResultPaginationDTO getAllRoles(RoleFilterRequest filter, Pageable pageable);

    RoleResponse getRoleById(Long id);

    RoleResponse createRole(CreateRoleRequest request);

    RoleResponse updateRole(Long id, UpdateRoleRequest request);

    void deleteRole(Long id);
}
