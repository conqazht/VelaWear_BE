package vn.conganh.commercial.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.user.dto.CreateUserRequest;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@Rollback
@DisplayName("Module User - UserController")
class UserControllerTest extends AbstractIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /users - 201: tạo user thành công khi dữ liệu hợp lệ")
        void createUser_validRequest_returnsCreatedUser() throws Exception {
            // Arrange
            CreateUserRequest request = validRequest(
                    "Controller Test User",
                    "controller.post.user@velawear.local",
                    "password123");

            // Act & Assert
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.fullName", is("Controller Test User")))
                    .andExpect(jsonPath("$.data.email", is("controller.post.user@velawear.local")))
                    .andExpect(jsonPath("$.data.password").doesNotExist());

            Optional<User> savedUser = userRepository.findByEmailAndDeletedAtIsNull(
                    "controller.post.user@velawear.local");
            assertThat(savedUser).isPresent();
            assertThat(savedUser.get().getFullName()).isEqualTo("Controller Test User");
            assertThat(savedUser.get().getPassword()).isNotEqualTo("password123");
            assertThat(savedUser.get().getPassword()).startsWith("$2");
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /users - 400: từ chối khi tên để trống")
        void createUser_blankName_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateUserRequest request = validRequest("", "blank.name@velawear.local", "password123");

            // Act & Assert
            assertValidationFailure(request, "blank.name@velawear.local");
        }

        @Test
        @DisplayName("POST /users - 400: từ chối khi email để trống")
        void createUser_blankEmail_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateUserRequest request = validRequest("Blank Email User", "", "password123");

            // Act & Assert
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(userRepository.findAll()).noneMatch(user -> user.getFullName().equals("Blank Email User"));
        }

        @Test
        @DisplayName("POST /users - 400: từ chối khi email sai định dạng")
        void createUser_invalidEmail_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateUserRequest request = validRequest("Invalid Email User", "not-an-email", "password123");

            // Act & Assert
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));

            assertThat(userRepository.findAll()).noneMatch(user -> user.getFullName().equals("Invalid Email User"));
        }

        @Test
        @DisplayName("POST /users - 400: từ chối khi password quá ngắn")
        void createUser_shortPassword_returnsBadRequestAndDoesNotSave() throws Exception {
            // Arrange
            CreateUserRequest request = validRequest("Short Password User", "short.password@velawear.local", "12345");

            // Act & Assert
            assertValidationFailure(request, "short.password@velawear.local");
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /users - 409: từ chối khi email đã tồn tại")
        void createUser_duplicateEmail_returnsConflictAndDoesNotCreateNewUser() throws Exception {
            // Arrange
            User existingUser = new User();
            existingUser.setFullName("Existing User");
            existingUser.setEmail("duplicate.user@velawear.local");
            existingUser.setPassword("$2a$10$alreadyencoded");
            existingUser.setBirthDate(LocalDate.of(1999, 1, 1));
            existingUser.setGender(UserGender.OTHER);
            userRepository.save(existingUser);

            CreateUserRequest request = validRequest(
                    "Duplicate User",
                    "duplicate.user@velawear.local",
                    "password123");

            long countBefore = userRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + adminToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.statusCode").value(409));

            assertThat(userRepository.count()).isEqualTo(countBefore);
        }
    }

    private void assertValidationFailure(CreateUserRequest request, String expectedEmail) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull(expectedEmail)).isEmpty();
    }

    private CreateUserRequest validRequest(String fullName, String email, String password) {
        return new CreateUserRequest(
                fullName,
                email,
                password,
                LocalDate.of(1998, 4, 10),
                null,
                UserGender.OTHER);
    }

    private String adminToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("admin@velawear.local")
                .claim("userId", 1L)
                .claim("roles", java.util.List.of("ROLE_ADMIN"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
