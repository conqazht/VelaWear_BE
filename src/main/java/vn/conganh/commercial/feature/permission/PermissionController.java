package vn.conganh.commercial.feature.permission;

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
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission and RBAC endpoint management")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getPermissions(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getAllPermissions(pageable)));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermission(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.getPermissionById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @RequestBody @Valid CreatePermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(permissionService.createPermission(request)));
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable Long id,
            @RequestBody @Valid UpdatePermissionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(permissionService.updatePermission(id, request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
