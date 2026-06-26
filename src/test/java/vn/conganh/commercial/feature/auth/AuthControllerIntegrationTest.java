package vn.conganh.commercial.feature.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@DisplayName("Module Auth - AuthController")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== Module Auth: POST /api/v1/auth/login ==========

    @Test
    @DisplayName("POST /auth/login - 200: đăng nhập thành công và trả về token")
    void login_validCredentials_returnsTokenResponse() throws Exception {
        // Arrange
        User user = new User();
        user.setFullName("Integration User");
        user.setEmail("integration.user@velawear.local");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setBirthDate(LocalDate.of(1999, 1, 1));
        user.setGender(UserGender.OTHER);
        User savedUser = userRepository.save(user);

        jdbcTemplate.update("""
                insert into user_role (user_id, role_id)
                select ?, r.id
                from roles r
                where r.name = 'USER'
                on conflict do nothing
                """, savedUser.getId());

        LoginRequest request = new LoginRequest("integration.user@velawear.local", "password123");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    @DisplayName("POST /auth/login - 401: từ chối đăng nhập khi sai thông tin")
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("missing@velawear.local", "password123");

        // Act & Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
