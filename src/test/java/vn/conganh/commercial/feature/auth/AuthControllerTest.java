package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Auth - AuthController")
class AuthControllerTest extends AuthenticatedIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("POST /auth/login - 200: đăng nhập thành công và trả về token")
        void login_validCredentials_returnsTokenResponse() throws Exception {
            // Arrange
            userRepository.save(user("auth.login@velawear.local", "Password123!"));
            LoginRequest request = new LoginRequest("auth.login@velawear.local", "Password123!");
            long refreshTokenCountBefore = refreshTokenRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("refresh_token=")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("HttpOnly")));

            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
        }

        @Test
        @DisplayName("POST /auth/register - 201: đăng ký user thành công và gắn role USER")
        void register_validRequest_returnsCreatedUserAndAssignsUserRole() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest(
                    "Register User",
                    "auth.register@velawear.local",
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.id").exists())
                    .andExpect(jsonPath("$.data.fullName").value("Register User"))
                    .andExpect(jsonPath("$.data.email").value("auth.register@velawear.local"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());

            User savedUser = userRepository.findByEmailAndDeletedAtIsNull("auth.register@velawear.local")
                    .orElseThrow();
            assertThat(savedUser.getPassword()).isNotEqualTo("Password123!");
            Integer roleCount = jdbcTemplate.queryForObject("""
                    select count(*)
                    from user_role ur
                    join roles r on r.id = ur.role_id
                    where ur.user_id = ? and r.name = 'USER'
                    """, Integer.class, savedUser.getId());
            assertThat(roleCount).isEqualTo(1);
        }

        @Test
        @DisplayName("POST /auth/refresh - 200: giữ nguyên refresh token và trả về token mới")
        void refreshToken_validRefreshToken_returnsSameRefreshTokenAndNewAccessToken() throws Exception {
            // Arrange
            userRepository.save(user("auth.refresh@velawear.local", "Password123!"));
            String oldRefreshToken = loginAndExtractRefreshToken("auth.refresh@velawear.local", "Password123!");
            long refreshTokenCountBefore = refreshTokenRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshTokenRequest(oldRefreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").value(is(oldRefreshToken)))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("refresh_token=" + oldRefreshToken)));

            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore);
            assertThat(countRevokedRefreshTokens()).isEqualTo(0);
        }

        @Test
        @DisplayName("POST /auth/logout - 200: revoke refresh token")
        void logout_validRefreshToken_revokesRefreshToken() throws Exception {
            // Arrange
            userRepository.save(user("auth.logout@velawear.local", "Password123!"));
            String refreshToken = loginAndExtractRefreshToken("auth.logout@velawear.local", "Password123!");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("Max-Age=0")));

            assertThat(countRevokedRefreshTokens()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("GET /auth/me - 200: trả về thông tin user hiện tại")
        void getMe_validAccessToken_returnsCurrentUser() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + adminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.data.email").value("admin@velawear.local"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("POST /auth/login - 400: từ chối khi email sai định dạng")
        void login_invalidEmail_returnsBadRequest() throws Exception {
            // Arrange
            LoginRequest request = new LoginRequest("not-an-email", "Password123!");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));
        }

        @Test
        @DisplayName("POST /auth/register - 400: từ chối khi password ngắn hơn 8 ký tự")
        void register_shortPassword_returnsBadRequest() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest(
                    "Register User",
                    "auth.register.short@velawear.local",
                    "1234567",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400));
        }
    }

    @Nested
    @DisplayName("Business errors")
    class BusinessErrors {

        @Test
        @DisplayName("POST /auth/login - 401: từ chối khi password sai")
        void login_wrongPassword_returnsUnauthorizedAndDoesNotCreateRefreshToken() throws Exception {
            // Arrange
            userRepository.save(user("auth.wrong-password@velawear.local", "Password123!"));
            LoginRequest request = new LoginRequest("auth.wrong-password@velawear.local", "WrongPassword!");
            long refreshTokenCountBefore = refreshTokenRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusCode").value(401));

            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore);
        }

        @Test
        @DisplayName("POST /auth/register - 409: từ chối khi email đã tồn tại")
        void register_duplicateEmail_returnsConflict() throws Exception {
            // Arrange
            userRepository.save(user("auth.duplicate@velawear.local", "Password123!"));
            RegisterRequest request = new RegisterRequest(
                    "Duplicate User",
                    "auth.duplicate@velawear.local",
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);
            long userCountBefore = userRepository.count();

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.statusCode").value(409));

            assertThat(userRepository.count()).isEqualTo(userCountBefore);
        }

        @Test
        @DisplayName("POST /auth/refresh - 401: từ chối khi refresh token không hợp lệ")
        void refreshToken_invalidRefreshToken_returnsUnauthorized() throws Exception {
            // Arrange
            RefreshTokenRequest request = new RefreshTokenRequest("missing-refresh-token");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusCode").value(401));
        }

        @Test
        @DisplayName("GET /auth/me - 401: từ chối khi không có access token")
        void getMe_missingAccessToken_returnsUnauthorized() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private String loginAndExtractRefreshToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
    }

    private Long countRevokedRefreshTokens() {
        refreshTokenRepository.flush();
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where revoked = true",
                Long.class);
    }

    private User user(String email, String rawPassword) {
        User user = new User();
        user.setFullName("Auth Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.OTHER);
        return user;
    }
}
