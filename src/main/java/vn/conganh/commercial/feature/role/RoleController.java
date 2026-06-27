package vn.conganh.commercial.feature.role;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role and RBAC assignment endpoints")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getRoles(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles(pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.getRoleById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@RequestBody @Valid CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(roleService.createRole(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @RequestBody @Valid UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.updateRole(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
