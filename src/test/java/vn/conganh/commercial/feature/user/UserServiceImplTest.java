package vn.conganh.commercial.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRequest;
import vn.conganh.commercial.feature.user.dto.UserResponse;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module User - UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("Create user")
    class CreateUser {

        @Test
        @DisplayName("createUser - tạo user thành công và encode password trước khi lưu")
        void createUser_validRequest_savesUserWithEncodedPassword() {
            // Arrange
            CreateUserRequest request = validRequest("Nguyen Van A", "A@Example.COM", "password123");

            when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);
                return user;
            });

            // Act
            UserResponse response = userService.createUser(request);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.fullName()).isEqualTo("Nguyen Van A");
            assertThat(response.email()).isEqualTo("a@example.com");
            verify(passwordEncoder).encode("password123");
            verify(userRepository).save(argThat(user ->
                    "$2a$10$encoded".equals(user.getPassword())
                            && !"password123".equals(user.getPassword())));
        }

        @Test
        @DisplayName("createUser - kiểm tra email tồn tại trước khi save")
        void createUser_validRequest_checksEmailBeforeSave() {
            // Arrange
            CreateUserRequest request = validRequest("Nguyen Van A", "a@example.com", "password123");

            when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            userService.createUser(request);

            // Assert
            InOrder inOrder = inOrder(userRepository);
            inOrder.verify(userRepository).existsByEmail("a@example.com");
            inOrder.verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("createUser - ném DuplicateResourceException khi email đã tồn tại")
        void createUser_duplicateEmail_throwsDuplicateResourceException() {
            // Arrange
            CreateUserRequest request = validRequest("Nguyen Van A", "existing@example.com", "password123");

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("email")
                    .hasMessageContaining("existing@example.com");
        }

        @Test
        @DisplayName("createUser - không gọi save khi email đã tồn tại")
        void createUser_duplicateEmail_doesNotSaveUser() {
            // Arrange
            CreateUserRequest request = validRequest("Nguyen Van A", "existing@example.com", "password123");

            when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get users")
    class GetUsers {

        @Test
        @DisplayName("getAllUsers - trả về danh sách user chưa bị xóa mềm")
        void getAllUsers_existingActiveUsers_returnsResponses() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            User user = activeUser(1L, "user@example.com");
            when(userRepository.findAllByDeletedAtIsNull(pageable))
                    .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

            // Act
            ResultPaginationDTO responses = userService.getAllUsers(pageable);

            // Assert
            assertThat(responses.result()).hasSize(1);
            assertThat(responses.result()).extracting("id").containsExactly(1L);
            assertThat(responses.result()).extracting("email").containsExactly("user@example.com");
            assertThat(responses.meta().page()).isEqualTo(1);
        }

        @Test
        @DisplayName("getUserById - trả về user khi id tồn tại và chưa bị xóa mềm")
        void getUserById_existingActiveUser_returnsResponse() {
            // Arrange
            User user = activeUser(1L, "user@example.com");
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

            // Act
            UserResponse response = userService.getUserById(1L);

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("getUserById - ném ResourceNotFoundException khi user không tồn tại")
        void getUserById_missingUser_throwsResourceNotFoundException() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.getUserById(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("id")
                    .hasMessageContaining("404");
        }
    }

    @Nested
    @DisplayName("Update user")
    class UpdateUser {

        @Test
        @DisplayName("updateUser - cập nhật thông tin user thành công")
        void updateUser_existingActiveUser_savesUpdatedUser() {
            // Arrange
            User user = activeUser(1L, "user@example.com");
            UpdateUserRequest request = new UpdateUserRequest(
                    "Updated User",
                    LocalDate.of(1999, 1, 1),
                    "avatar.png",
                    UserGender.FEMALE);

            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UserResponse response = userService.updateUser(1L, request);

            // Assert
            assertThat(response.fullName()).isEqualTo("Updated User");
            assertThat(response.birthDate()).isEqualTo(LocalDate.of(1999, 1, 1));
            assertThat(response.avatar()).isEqualTo("avatar.png");
            assertThat(response.gender()).isEqualTo(UserGender.FEMALE);
            verify(userRepository).save(argThat(savedUser ->
                    "Updated User".equals(savedUser.getFullName())
                            && UserGender.FEMALE.equals(savedUser.getGender())));
        }

        @Test
        @DisplayName("updateUser - không gọi save khi user không tồn tại")
        void updateUser_missingUser_doesNotSaveUser() {
            // Arrange
            UpdateUserRequest request = new UpdateUserRequest(
                    "Updated User",
                    LocalDate.of(1999, 1, 1),
                    null,
                    UserGender.OTHER);
            when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.updateUser(404L, request))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete user")
    class DeleteUser {

        @Test
        @DisplayName("deleteUser - cập nhật deletedAt để xóa mềm user")
        void deleteUser_existingActiveUser_setsDeletedAtBeforeSave() {
            // Arrange
            User user = activeUser(1L, "user@example.com");
            when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            userService.deleteUser(1L);

            // Assert
            verify(userRepository).save(argThat(savedUser -> savedUser.getDeletedAt() != null));
        }

        @Test
        @DisplayName("deleteUser - không gọi save khi user không tồn tại")
        void deleteUser_missingUser_doesNotSaveUser() {
            // Arrange
            when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> userService.deleteUser(404L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(userRepository, never()).save(any());
        }
    }

    private CreateUserRequest validRequest(String fullName, String email, String password) {
        return new CreateUserRequest(
                fullName,
                email,
                password,
                LocalDate.of(2000, 1, 1),
                null,
                UserGender.MALE);
    }

    private User activeUser(Long id, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }
}
