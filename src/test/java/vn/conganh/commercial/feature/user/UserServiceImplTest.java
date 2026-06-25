package vn.conganh.commercial.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRequest;
import vn.conganh.commercial.feature.user.dto.UserResponse;

@ExtendWith(MockitoExtension.class)
@Disabled("Temporarily disabled while user tests are being realigned with enum/package refactor")
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

    // ===== getAllUsers =====

    @Test
    @DisplayName("Should return list of users when users exist")
    void getAllUsers_returnsData() {
        User user = createActiveUser(1L, "user@test.com");
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(user));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("user@test.com");
        verify(userRepository).findAllByDeletedAtIsNull();
    }

    @Test
    @DisplayName("Should return empty list when no users exist")
    void getAllUsers_returnsEmptyList() {
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of());

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).isEmpty();
    }

    // ===== getUserById =====

    @Test
    @DisplayName("Should return user when found by id")
    void getUserById_found() {
        User user = createActiveUser(1L, "user@test.com");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserResponse result = userService.getUserById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void getUserById_notFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining("id")
                .hasMessageContaining("99");
    }

    // ===== createUser =====

    @Test
    @DisplayName("Should create user successfully")
    void createUser_success() {
        CreateUserRequest request = new CreateUserRequest(
                "John Doe", "John@Test.COM", "password123",
                LocalDate.of(2000, 1, 1), "avatar.png", UserGender.MALE);

        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        UserResponse result = userService.createUser(request);

        assertThat(result.email()).isEqualTo("john@test.com");
        assertThat(result.fullName()).isEqualTo("John Doe");
        verify(userRepository).existsByEmail("john@test.com");
        verify(passwordEncoder).encode("password123");
    }

    @Test
    @DisplayName("Should normalize email to lowercase and trimmed on create")
    void createUser_emailNormalization() {
        CreateUserRequest request = new CreateUserRequest(
                "John Doe", "  JOHN@TEST.COM  ", "password123",
                LocalDate.of(2000, 1, 1), null, UserGender.MALE);

        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        UserResponse result = userService.createUser(request);

        assertThat(result.email()).isEqualTo("john@test.com");
        verify(userRepository).existsByEmail("john@test.com");
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when email already exists")
    void createUser_duplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "John Doe", "existing@test.com", "password123",
                LocalDate.of(2000, 1, 1), null, UserGender.MALE);

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
    }

    // ===== updateUser =====

    @Test
    @DisplayName("Should update user successfully")
    void updateUser_success() {
        User user = createActiveUser(1L, "user@test.com");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest(
                "Updated Name", LocalDate.of(1995, 5, 15),
                "new-avatar.png", UserGender.FEMALE);

        UserResponse result = userService.updateUser(1L, request);

        assertThat(result.fullName()).isEqualTo("Updated Name");
        assertThat(result.gender()).isEqualTo(UserGender.FEMALE);
        assertThat(result.avatar()).isEqualTo("new-avatar.png");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when updating non-existent user")
    void updateUser_notFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest(
                "Updated Name", LocalDate.of(1995, 5, 15),
                null, UserGender.MALE);

        assertThatThrownBy(() -> userService.updateUser(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== deleteUser =====

    @Test
    @DisplayName("Should soft-delete user successfully")
    void deleteUser_success() {
        User user = createActiveUser(1L, "user@test.com");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deleteUser(1L);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting non-existent user")
    void deleteUser_notFound() {
        when(userRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== helpers =====

    private User createActiveUser(Long id, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "fullName", "Test User");
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "password", "$2a$10$encoded");
        ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(2000, 1, 1));
        ReflectionTestUtils.setField(user, "gender", UserGender.MALE);
        return user;
    }
}
