package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.config.JwtConfig;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshToken;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.role.Role;
import vn.conganh.commercial.feature.role.RoleRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserHasRole;
import vn.conganh.commercial.feature.user.UserHasRoleRepository;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.feature.user.dto.UserResponse;
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
    private RoleRepository roleRepository;

    @Mock
    private UserHasRoleRepository userHasRoleRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET_KEY, 900, 259200);
        JwtConfig jwtConfig = new JwtConfig(jwtProperties);
        authService = new AuthServiceImpl(
                authenticationManager,
                userRepository,
                roleRepository,
                userHasRoleRepository,
                refreshTokenService,
                passwordEncoder,
                jwtConfig.jwtEncoder(),
                jwtConfig.jwtDecoder(),
                jwtProperties);
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
            assertThat(response.tokenType()).isEqualTo("Bearer");
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

        @Test
        @DisplayName("authenticate - refresh token là JWT có type refresh và userId")
        void authenticate_validCredentials_generatesRefreshJwtWithExpectedClaims() throws ParseException {
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
            SignedJWT signedJWT = SignedJWT.parse(response.refreshToken());
            assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("admin@example.com");
            assertThat(signedJWT.getJWTClaimsSet().getLongClaim("userId")).isEqualTo(1L);
            assertThat(signedJWT.getJWTClaimsSet().getStringClaim("type")).isEqualTo("refresh");
            assertThat(signedJWT.getJWTClaimsSet().getJWTID()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        @DisplayName("register - tạo user mới và gắn role USER")
        void register_validRequest_savesUserAndAssignsUserRole() {
            // Arrange
            RegisterRequest request = new RegisterRequest(
                    "New User",
                    "NEW.USER@Example.com",
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);
            Role role = role(4L, "USER");
            when(userRepository.existsByEmail("new.user@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User savedUser = invocation.getArgument(0);
                ReflectionTestUtils.setField(savedUser, "id", 2L);
                return savedUser;
            });
            when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));

            // Act
            UserResponse response = authService.register(request);

            // Assert
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.email()).isEqualTo("new.user@example.com");
            verify(userRepository).save(argThat(user ->
                    "$2a$10$encoded".equals(user.getPassword())
                            && !"Password123!".equals(user.getPassword())));
            verify(userHasRoleRepository).save(argThat(userHasRole ->
                    userHasRole.getUser().getId().equals(2L)
                            && "USER".equals(userHasRole.getRole().getName())));
        }

        @Test
        @DisplayName("register - không lưu user khi email đã tồn tại")
        void register_duplicateEmail_throwsDuplicateResourceExceptionAndDoesNotSave() {
            // Arrange
            RegisterRequest request = new RegisterRequest(
                    "New User",
                    "new.user@example.com",
                    "Password123!",
                    LocalDate.of(2000, 1, 1),
                    null,
                    UserGender.OTHER);
            when(userRepository.existsByEmail("new.user@example.com")).thenReturn(true);

            // Act & Assert
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(userRepository, never()).save(any());
            verify(userHasRoleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Refresh token")
    class RefreshTokenGroup {

        @Test
        @DisplayName("refreshToken - rotate refresh token và trả về access token mới")
        void refreshToken_validRefreshToken_returnsNewTokenPair() {
            // Arrange
            User user = user(1L);
            RefreshToken refreshToken = refreshToken(user, validRefreshJwt(user));
            when(refreshTokenService.findValidRefreshToken(refreshToken.getToken())).thenReturn(refreshToken);
            when(userHasRoleRepository.findRolesByUserId(1L)).thenReturn(List.of(role(1L, "ADMIN")));

            // Act
            TokenResponse response = authService.refreshToken(new RefreshTokenRequest(refreshToken.getToken()));

            // Assert
            assertThat(refreshToken.isRevoked()).isTrue();
            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.refreshToken()).isNotBlank();
            verify(refreshTokenService).createRefreshToken(argThat(request ->
                    "Chrome".equals(request.deviceInfo())
                            && "127.0.0.1".equals(request.ipAddress())));
        }
    }

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        @DisplayName("logout - revoke refresh token")
        void logout_validRefreshToken_revokesToken() {
            // Arrange
            RefreshTokenRequest request = new RefreshTokenRequest("raw-refresh-token");

            // Act
            authService.logout(request);

            // Assert
            verify(refreshTokenService).revokeRefreshToken("raw-refresh-token");
        }
    }

    @Nested
    @DisplayName("Get me")
    class GetMe {

        @Test
        @DisplayName("getMe - trả về thông tin user kèm roles")
        void getMe_existingUser_returnsUserWithRoles() {
            // Arrange
            User user = user(1L);
            Role role = role(2L, "SUPER_ADMIN");
            when(userRepository.findByEmailAndDeletedAtIsNull("admin@example.com")).thenReturn(Optional.of(user));
            when(userHasRoleRepository.findRolesByUserId(1L)).thenReturn(List.of(role));

            // Act
            UserResponse response = authService.getMe("admin@example.com");

            // Assert
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.roles())
                    .extracting(UserResponse.RoleSummaryResponse::id, UserResponse.RoleSummaryResponse::name)
                    .containsExactly(tuple(2L, "SUPER_ADMIN"));
            assertThat(response.permissions()).isEmpty();
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

    private Role role(Long id, String name) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "id", id);
        role.setName(name);
        return role;
    }

    private RefreshToken refreshToken(User user) {
        return refreshToken(user, "hashed-token");
    }

    private RefreshToken refreshToken(User user, String rawToken) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(rawToken);
        refreshToken.setExpiresAt(Instant.parse("2026-12-31T00:00:00Z"));
        refreshToken.setDeviceInfo("Chrome");
        refreshToken.setIpAddress("127.0.0.1");
        return refreshToken;
    }

    private String validRefreshJwt(User user) {
        JwtProperties jwtProperties = new JwtProperties(SECRET_KEY, 900, 259200);
        JwtConfig jwtConfig = new JwtConfig(jwtProperties);
        Instant now = Instant.now();
        org.springframework.security.oauth2.jwt.JwtClaimsSet claims =
                org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                        .id("refresh-jti")
                        .subject(user.getEmail())
                        .claim("userId", user.getId())
                        .claim("type", "refresh")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(259200))
                        .build();
        org.springframework.security.oauth2.jwt.JwsHeader header =
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS512).build();
        return jwtConfig.jwtEncoder()
                .encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
