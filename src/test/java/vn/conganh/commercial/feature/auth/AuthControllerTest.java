package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
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
                    .andExpect(jsonPath("$.data.expiresIn").value(900));

            assertThat(refreshTokenRepository.count()).isEqualTo(refreshTokenCountBefore + 1);
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
