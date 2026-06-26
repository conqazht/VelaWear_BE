package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.UserGender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Auth - AuthServiceImpl")
class AuthServiceImplTest {

    private static final String SECRET_KEY =
            "0123456789012345678901234567890123456789012345678901234567890123";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET_KEY, 900, 604800);
        authService = new AuthServiceImpl(authenticationManager, userRepository, refreshTokenService, jwtProperties);
    }

    @Nested
    @DisplayName("Authenticate")
    class Authenticate {

        @Test
        @DisplayName("authenticate - đăng nhập thành công và tạo access token cùng refresh token")
        void authenticate_validCredentials_returnsTokenResponse() {
            // Arrange
            LoginRequest request = new LoginRequest("admin@example.com", "Password123!");
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "admin@example.com",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userRepository.findByEmailAndDeletedAtIsNull("admin@example.com"))
                    .thenReturn(Optional.of(user(1L)));

            // Act
            TokenResponse response = authService.authenticate(request);

            // Assert
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(900);
            verify(refreshTokenService).createRefreshToken(argThat(refreshToken ->
                    refreshToken.userId().equals(1L)
                            && refreshToken.token().equals(response.refreshToken())
                            && refreshToken.expiresAt() != null));
        }

        @Test
        @DisplayName("authenticate - access token chứa subject, userId và roles")
        void authenticate_validCredentials_generatesJwtWithExpectedClaims() throws ParseException {
            // Arrange
            LoginRequest request = new LoginRequest("admin@example.com", "Password123!");
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "admin@example.com",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(userRepository.findByEmailAndDeletedAtIsNull("admin@example.com"))
                    .thenReturn(Optional.of(user(1L)));

            // Act
            TokenResponse response = authService.authenticate(request);

            // Assert
            SignedJWT signedJWT = SignedJWT.parse(response.accessToken());
            assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("admin@example.com");
            assertThat(signedJWT.getJWTClaimsSet().getLongClaim("userId")).isEqualTo(1L);
            assertThat(signedJWT.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("ROLE_ADMIN");
        }
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("Admin User");
        user.setEmail("admin@example.com");
        user.setPassword("$2a$10$encoded");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(UserGender.MALE);
        return user;
    }
}
