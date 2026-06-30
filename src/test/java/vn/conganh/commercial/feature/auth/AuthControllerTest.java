package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import jakarta.servlet.http.Cookie;
import vn.conganh.commercial.feature.auth.dto.ChangeEmailRequest;
import vn.conganh.commercial.feature.auth.dto.ForgotPasswordResetRequest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenRepository;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
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
    private RefreshTokenSessionService refreshTokenSessionService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

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

            // Seed verified marker in Redis
            redisTemplate.opsForValue().set("auth:otp:verified:REGISTER:auth.register@velawear.local", "true", 5, java.util.concurrent.TimeUnit.MINUTES);

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
        @DisplayName("POST /auth/refresh - 200: rotate refresh token và trả về token mới")
        void refreshToken_validRefreshToken_rotatesRefreshTokenAndReturnsNewToken() throws Exception {
            // Arrange
            userRepository.save(user("auth.refresh@velawear.local", "Password123!"));
            String oldRefreshToken = loginAndExtractRefreshToken("auth.refresh@velawear.local", "Password123!");
            String oldJti = jwtId(oldRefreshToken);
            long refreshTokenCountBefore = refreshTokenRepository.count();

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", oldRefreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("refresh_token=")))
                    .andReturn();

            String newRefreshToken = JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
            String newJti = jwtId(newRefreshToken);
            assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
            assertThat(countRevokedRefreshTokens()).isEqualTo(1);
            assertThat(refreshTokenSessionService.find(oldJti)).isEmpty();
            assertThat(refreshTokenSessionService.find(newJti)).isPresent();
        }

        @Test
        @DisplayName("POST /auth/login - 200: ghi refresh session vào Redis và audit row vào PostgreSQL")
        void login_validCredentials_writesRedisSessionAndPostgresAuditRow() throws Exception {
            // Arrange
            userRepository.save(user("auth.login.redis@velawear.local", "Password123!"));
            long refreshTokenCountBefore = refreshTokenRepository.count();

            // Act
            String refreshToken = loginAndExtractRefreshToken("auth.login.redis@velawear.local", "Password123!");

            // Assert
            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
            assertThat(refreshTokenSessionService.find(jwtId(refreshToken))).isPresent();
        }

        @Test
        @DisplayName("POST /auth/logout - 200: revoke refresh token")
        void logout_validRefreshToken_revokesRefreshToken() throws Exception {
            // Arrange
            userRepository.save(user("auth.logout@velawear.local", "Password123!"));
            String refreshToken = loginAndExtractRefreshToken("auth.logout@velawear.local", "Password123!");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer " + tokenWithRoles(
                                    "auth.logout@velawear.local",
                                    userRepository.findByEmailAndDeletedAtIsNull("auth.logout@velawear.local")
                                            .orElseThrow()
                                            .getId(),
                                    List.of("ROLE_USER")))
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("Max-Age=0")));

            assertThat(countRevokedRefreshTokens()).isGreaterThanOrEqualTo(1);
            assertThat(refreshTokenSessionService.find(jwtId(refreshToken))).isEmpty();
        }

        @Test
        @DisplayName("POST /auth/logout - 200: thành công ngay cả khi không cung cấp refresh token")
        void logout_noRefreshToken_returnsOkAndClearsCookie() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("Max-Age=0")));
        }

        @Test
        @DisplayName("POST /auth/refresh - 401: từ chối refresh token sau khi đã logout")
        void refresh_afterLogout_returnsUnauthorized() throws Exception {
            // Arrange
            userRepository.save(user("auth.logout.refresh@velawear.local", "Password123!"));
            String refreshToken = loginAndExtractRefreshToken("auth.logout.refresh@velawear.local", "Password123!");

            // Logout (thu hồi token)
            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isOk());

            // Act & Assert (thử dùng token đã thu hồi để refresh)
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusCode").value(401))
                    .andExpect(jsonPath("$.message").value("Refresh session is expired or revoked"));
        }

        @Test
        @DisplayName("POST /auth/refresh - 401: Redis miss không fallback PostgreSQL và clear cookie")
        void refreshToken_redisMiss_returnsUnauthorizedWithoutPostgresFallback() throws Exception {
            // Arrange
            userRepository.save(user("auth.redis.miss@velawear.local", "Password123!"));
            String refreshToken = loginAndExtractRefreshToken("auth.redis.miss@velawear.local", "Password123!");
            refreshTokenSessionService.delete(jwtId(refreshToken));

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", refreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusCode").value(401))
                    .andExpect(jsonPath("$.message").value("Refresh session is expired or revoked"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("Max-Age=0")));

            assertThat(countRevokedRefreshTokens()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("POST /auth/refresh - 401: refresh token cũ sau rotation không dùng lại được")
        void refreshToken_reusedOldTokenAfterRotation_returnsUnauthorized() throws Exception {
            // Arrange
            userRepository.save(user("auth.rotate.reuse@velawear.local", "Password123!"));
            String oldRefreshToken = loginAndExtractRefreshToken("auth.rotate.reuse@velawear.local", "Password123!");
            refreshAndExtractRefreshToken(oldRefreshToken);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", oldRefreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.statusCode").value(401))
                    .andExpect(jsonPath("$.message").value("Refresh session is expired or revoked"));
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
            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie("refresh_token", "missing-refresh-token")))
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

    private String refreshAndExtractRefreshToken(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.refreshToken");
    }

    private String jwtId(String rawJwt) throws ParseException {
        return SignedJWT.parse(rawJwt).getJWTClaimsSet().getJWTID();
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

    @Nested
    @DisplayName("OTP Protected Auth Flows")
    class OtpAuthFlows {

        @Test
        @DisplayName("POST /auth/register - 400: fails if email not verified with OTP")
        void register_unverifiedEmail_fails() throws Exception {
            // Arrange
            RegisterRequest request = new RegisterRequest(
                    "New User",
                    "unverified@velawear.local",
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.message").value("Email address has not been verified with OTP."));
        }

        @Test
        @DisplayName("POST /auth/register - 201: succeeds if email verified with OTP")
        void register_verifiedEmail_success() throws Exception {
            // Arrange
            String email = "verified-register@velawear.local";
            RegisterRequest request = new RegisterRequest(
                    "New User",
                    email,
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);

            // Seed verified marker in Redis
            redisTemplate.opsForValue().set("auth:otp:verified:REGISTER:" + email, "true", 5, java.util.concurrent.TimeUnit.MINUTES);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.statusCode").value(201))
                    .andExpect(jsonPath("$.data.email").value(email));

            // Verify marker consumed
            assertThat(redisTemplate.hasKey("auth:otp:verified:REGISTER:" + email)).isFalse();
        }

        @Test
        @DisplayName("POST /auth/forgot-password/reset - 400: fails if email not verified with OTP")
        void forgotPassword_unverifiedEmail_fails() throws Exception {
            // Arrange
            userRepository.save(user("unverified-forgot@velawear.local", "OldPassword123!"));
            ForgotPasswordResetRequest request = new ForgotPasswordResetRequest(
                    "unverified-forgot@velawear.local",
                    "NewPassword123!");

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.statusCode").value(400))
                    .andExpect(jsonPath("$.message").value("Email address has not been verified with OTP."));
        }

        @Test
        @DisplayName("POST /auth/forgot-password/reset - 200: succeeds if email verified with OTP")
        void forgotPassword_verifiedEmail_success() throws Exception {
            // Arrange
            String email = "verified-forgot@velawear.local";
            userRepository.save(user(email, "OldPassword123!"));
            ForgotPasswordResetRequest request = new ForgotPasswordResetRequest(
                    email,
                    "NewPassword123!");

            // Seed verified marker in Redis
            redisTemplate.opsForValue().set("auth:otp:verified:FORGOT_PASSWORD:" + email, "true", 5, java.util.concurrent.TimeUnit.MINUTES);

            // Act & Assert
            mockMvc.perform(post("/api/v1/auth/forgot-password/reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Password reset successfully"));

            // Verify marker consumed
            assertThat(redisTemplate.hasKey("auth:otp:verified:FORGOT_PASSWORD:" + email)).isFalse();

            // Verify user password updated by attempting login
            LoginRequest loginRequest = new LoginRequest(email, "NewPassword123!");
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PUT /auth/me/email - 200: updates email successfully with verified OTP")
        void changeEmail_verifiedNewEmail_success() throws Exception {
            // Arrange
            String currentEmail = "current-change@velawear.local";
            String newEmail = "new-change@velawear.local";
            User savedUser = userRepository.save(user(currentEmail, "Password123!"));

            // Seed verified marker in Redis for the new email
            redisTemplate.opsForValue().set("auth:otp:verified:CHANGE_EMAIL:" + newEmail, "true", 5, java.util.concurrent.TimeUnit.MINUTES);

            // Generate user token
            String token = tokenWithRoles(currentEmail, savedUser.getId(), List.of("ROLE_USER"));
            ChangeEmailRequest request = new ChangeEmailRequest(newEmail);

            // Act & Assert
            mockMvc.perform(put("/api/v1/auth/me/email")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value(200))
                    .andExpect(jsonPath("$.message").value("Email updated successfully"));

            // Verify marker consumed
            assertThat(redisTemplate.hasKey("auth:otp:verified:CHANGE_EMAIL:" + newEmail)).isFalse();

            // Verify database updated
            User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
            assertThat(updatedUser.getEmail()).isEqualTo(newEmail);
        }
    }
}
