package vn.conganh.commercial.feature.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.permission.Permission;
import vn.conganh.commercial.feature.role.dto.CreateRoleRequest;
import vn.conganh.commercial.feature.role.dto.RoleResponse;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Role - RoleServiceImpl")
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleRepository);
    }

    @Nested
    @DisplayName("Create role")
    class CreateRole {

        @Test
        @DisplayName("createRole - tạo role thành công khi name chưa tồn tại")
        void createRole_validRequest_returnsRoleResponse() {
            // Arrange
            CreateRoleRequest request = new CreateRoleRequest("TEST_ROLE", "desc");
            when(roleRepository.existsByName("TEST_ROLE")).thenReturn(false);
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> {
                Role role = invocation.getArgument(0);
                ReflectionTestUtils.setField(role, "id", 1L);
                return role;
            });

            // Act
            RoleResponse response = roleService.createRole(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("TEST_ROLE");
            assertThat(response.description()).isEqualTo("desc");
        }

        @Test
        @DisplayName("createRole - không gọi save khi name đã tồn tại")
        void createRole_duplicateName_throwsInvalidRequestExceptionAndDoesNotSave() {
            // Arrange
            CreateRoleRequest request = new CreateRoleRequest("ADMIN", "duplicate");
            when(roleRepository.existsByName("ADMIN")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> roleService.createRole(request))
                    .isInstanceOf(InvalidRequestException.class);
            verify(roleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read role")
    class ReadRole {

        @Test
        @DisplayName("getAllRoles - trả về danh sách role")
        void getAllRoles_existingRoles_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(roleRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(role(1L, "ADMIN")), pageable, 1));

            // Act
            ResultPaginationDTO responses = roleService.getAllRoles(pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("name").containsExactly("ADMIN");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getRoleById - ném ResourceNotFoundException khi không tìm thấy role")
        void getRoleById_missingRole_throwsResourceNotFoundException() {
            // Arrange
            when(roleRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> roleService.getRoleById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("getRoleById - trả về permissions của role")
        void getRoleById_existingRole_returnsPermissions() {
            // Arrange
            Role role = role(1L, "ADMIN");
            Permission permission = permission(2L, "UPLOAD_FILE", "/api/v1/files", "POST", "FILE");
            when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
            when(roleRepository.findPermissionsByRoleId(1L)).thenReturn(List.of(permission));

            // Act
            RoleResponse response = roleService.getRoleById(1L);

            // Assert
            assertThat(response.permissions())
                    .extracting(
                            permissionResponse -> permissionResponse.id(),
                            permissionResponse -> permissionResponse.name(),
                            permissionResponse -> permissionResponse.apiPath(),
                            permissionResponse -> permissionResponse.method(),
                            permissionResponse -> permissionResponse.module())
                    .containsExactly(tuple(2L, "UPLOAD_FILE", "/api/v1/files", "POST", "FILE"));
        }
    }

    @Nested
    @DisplayName("Update role")
    class UpdateRole {

        @Test
        @DisplayName("updateRole - cập nhật role thành công khi id tồn tại")
        void updateRole_existingRole_returnsUpdatedResponse() {
            // Arrange
            Role role = role(1L, "OLD");
            UpdateRoleRequest request = new UpdateRoleRequest("NEW", "new desc");
            when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
            when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            RoleResponse response = roleService.updateRole(1L, request);

            // Assert
            assertThat(response.name()).isEqualTo("NEW");
            assertThat(response.description()).isEqualTo("new desc");
        }
    }

    private Role role(Long id, String name) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", id);
        role.setName(name);
        role.setDescription("desc");
        return role;
    }

    private Permission permission(Long id, String name, String apiPath, String method, String module) {
        Permission permission = new Permission();
        ReflectionTestUtils.setField(permission, "id", id);
        permission.setName(name);
        permission.setApiPath(apiPath);
        permission.setMethod(method);
        permission.setModule(module);
        return permission;
    }
}
