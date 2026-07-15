package vn.conganh.commercial.feature.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.permission.dto.CreatePermissionRequest;
import vn.conganh.commercial.feature.permission.dto.PermissionResponse;
import vn.conganh.commercial.feature.permission.dto.UpdatePermissionRequest;
import vn.conganh.commercial.feature.role.Role;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Permission - PermissionServiceImpl")
class PermissionServiceImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private org.springframework.cache.CacheManager cacheManager;

    @Mock
    private Cache rolePermissionsCache;

    @Mock
    private vn.conganh.commercial.feature.role.RoleRepository roleRepository;

    private PermissionServiceImpl permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionServiceImpl(permissionRepository, cacheManager, roleRepository);
    }

    @Nested
    @DisplayName("Create permission")
    class CreatePermission {

        @Test
        @DisplayName("createPermission - tạo permission thành công khi path và method chưa tồn tại")
        void createPermission_validRequest_returnsPermissionResponse() {
            // Arrange
            CreatePermissionRequest request = new CreatePermissionRequest("VIEW_TEST", "/api/v1/tests", "GET", "TEST");
            when(permissionRepository.existsByApiPathAndMethod("/api/v1/tests", "GET")).thenReturn(false);
            when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> {
                Permission permission = invocation.getArgument(0);
                ReflectionTestUtils.setField(permission, "id", 1L);
                return permission;
            });

            // Act
            PermissionResponse response = permissionService.createPermission(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.apiPath()).isEqualTo("/api/v1/tests");
            assertThat(response.method()).isEqualTo("GET");
        }

        @Test
        @DisplayName("createPermission - không gọi save khi path và method đã tồn tại")
        void createPermission_duplicatePathAndMethod_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreatePermissionRequest request = new CreatePermissionRequest("VIEW_TEST", "/api/v1/tests", "GET", "TEST");
            when(permissionRepository.existsByApiPathAndMethod("/api/v1/tests", "GET")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> permissionService.createPermission(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(permissionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read permission")
    class ReadPermission {

        @Test
        @DisplayName("getAllPermissions - trả về danh sách permission")
        void getAllPermissions_existingPermissions_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(permissionRepository.findAll(ArgumentMatchers.<Specification<Permission>>any(), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(permission(1L)), pageable, 1));

            // Act
            ResultPaginationDTO responses = permissionService.getAllPermissions(null, pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("name").containsExactly("VIEW_TEST");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getPermissionById - ném ResourceNotFoundException khi không tìm thấy permission")
        void getPermissionById_missingPermission_throwsResourceNotFoundException() {
            // Arrange
            when(permissionRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> permissionService.getPermissionById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Update permission")
    class UpdatePermission {

        @Test
        @DisplayName("updatePermission - cập nhật permission thành công khi id tồn tại")
        void updatePermission_existingPermission_returnsUpdatedResponse() {
            // Arrange
            Permission permission = permission(1L);
            UpdatePermissionRequest request = new UpdatePermissionRequest("CREATE_TEST", "/api/v1/tests", "POST", "TEST");
            when(permissionRepository.findById(1L)).thenReturn(Optional.of(permission));
            when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(cacheManager.getCache("role_permissions")).thenReturn(rolePermissionsCache);
            when(roleRepository.findAll()).thenReturn(List.of(role("ADMIN"), role("USER")));

            // Act
            PermissionResponse response = permissionService.updatePermission(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("CREATE_TEST");
            assertThat(response.method()).isEqualTo("POST");
            verify(rolePermissionsCache).evictIfPresent("ADMIN");
            verify(rolePermissionsCache).evictIfPresent("USER");
        }
    }

    private Permission permission(Long id) {
        Permission permission = new Permission();
        ReflectionTestUtils.setField(permission, "id", id);
        permission.setName("VIEW_TEST");
        permission.setApiPath("/api/v1/tests");
        permission.setMethod("GET");
        permission.setModule("TEST");
        return permission;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
