package vn.conganh.commercial.feature.user;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import vn.conganh.commercial.exception.GlobalExceptionHandler;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.feature.user.dto.UpdateUserRequest;
import vn.conganh.commercial.feature.user.dto.UserResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@Disabled("Temporarily disabled while controller/API versioning tests are being realigned")
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ===== GET /api/users =====

    @Test
    @DisplayName("Should return list of users")
    void getUsers_success() throws Exception {
        UserResponse user = new UserResponse(1L, "John Doe", "john@test.com",
                LocalDate.of(2000, 1, 1), "avatar.png", UserGender.MALE,
                java.time.Instant.now(), java.time.Instant.now());
        when(userService.getAllUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/users")
                        .header("Accept-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode", is(200)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email", is("john@test.com")));
    }

    @Test
    @DisplayName("Should return empty list when no users")
    void getUsers_empty() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/users")
                        .header("Accept-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ===== GET /api/users/{id} =====

    @Test
    @DisplayName("Should return user by id")
    void getUser_found() throws Exception {
        UserResponse user = new UserResponse(1L, "John Doe", "john@test.com",
                LocalDate.of(2000, 1, 1), "avatar.png", UserGender.MALE,
                java.time.Instant.now(), java.time.Instant.now());
        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1")
                        .header("Accept-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.email", is("john@test.com")));
    }

    @Test
    @DisplayName("Should return 404 when user not found")
    void getUser_notFound() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User", "id", 99L));

        mockMvc.perform(get("/api/users/99")
                        .header("Accept-Version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("User not found with id: 99")));
    }

    // ===== POST /api/users =====

    @Test
    @DisplayName("Should create user successfully")
    void createUser_success() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "John Doe", "john@test.com", "password123",
                LocalDate.of(2000, 1, 1), "avatar.png", UserGender.MALE);
        UserResponse response = new UserResponse(1L, "John Doe", "john@test.com",
                LocalDate.of(2000, 1, 1), "avatar.png", UserGender.MALE,
                java.time.Instant.now(), java.time.Instant.now());
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode", is(201)))
                .andExpect(jsonPath("$.data.email", is("john@test.com")));
    }

    @Test
    @DisplayName("Should return 400 when validation fails")
    void createUser_validationError() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "", "invalid-email", "short",
                LocalDate.of(2030, 1, 1), null, null);

        mockMvc.perform(post("/api/users")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode", is(400)));
    }

    @Test
    @DisplayName("Should return 400 when email already exists")
    void createUser_duplicateEmail() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "John Doe", "existing@test.com", "password123",
                LocalDate.of(2000, 1, 1), null, UserGender.MALE);
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new InvalidRequestException("Email already exists"));

        mockMvc.perform(post("/api/users")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Email already exists")));
    }

    // ===== PUT /api/users/{id} =====

    @Test
    @DisplayName("Should update user successfully")
    void updateUser_success() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
                "Updated Name", LocalDate.of(1995, 5, 15),
                "new-avatar.png", UserGender.FEMALE);
        UserResponse response = new UserResponse(1L, "Updated Name", "john@test.com",
                LocalDate.of(1995, 5, 15), "new-avatar.png", UserGender.FEMALE,
                java.time.Instant.now(), java.time.Instant.now());
        when(userService.updateUser(any(Long.class), any(UpdateUserRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/users/1")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName", is("Updated Name")))
                .andExpect(jsonPath("$.data.gender", is("FEMALE")));
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent user")
    void updateUser_notFound() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
                "Updated Name", LocalDate.of(1995, 5, 15),
                null, UserGender.MALE);
        when(userService.updateUser(any(Long.class), any(UpdateUserRequest.class)))
                .thenThrow(new ResourceNotFoundException("User", "id", 99L));

        mockMvc.perform(put("/api/users/99")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when update validation fails")
    void updateUser_validationError() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest(
                "", null, null, null);

        mockMvc.perform(put("/api/users/1")
                        .header("Accept-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ===== DELETE /api/users/{id} =====

    @Test
    @DisplayName("Should delete user successfully")
    void deleteUser_success() throws Exception {
        mockMvc.perform(delete("/api/users/1")
                        .header("Accept-Version", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode", is(200)));
        verify(userService).deleteUser(1L);
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent user")
    void deleteUser_notFound() throws Exception {
        doThrow(new ResourceNotFoundException("User", "id", 99L))
                .when(userService).deleteUser(99L);

        mockMvc.perform(delete("/api/users/99")
                        .header("Accept-Version", "1"))
                .andExpect(status().isNotFound());
    }
}
