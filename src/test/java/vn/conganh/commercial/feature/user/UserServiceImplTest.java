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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
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

    private CreateUserRequest validRequest(String fullName, String email, String password) {
        return new CreateUserRequest(
                fullName,
                email,
                password,
                LocalDate.of(2000, 1, 1),
                null,
                UserGender.MALE);
    }
}
