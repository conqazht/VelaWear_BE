package vn.conganh.commercial.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.RefreshTokenSessionNotFoundException;
import vn.conganh.commercial.feature.auth.AuthTokenCodec;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.security.TokenBlacklistService;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSession;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
import vn.conganh.commercial.feature.user.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("Security - UserSessionService")
class UserSessionServiceTest {

    @Mock private AuthTokenCodec authTokenCodec;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private RefreshTokenSessionService refreshTokenSessionService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private JwtProperties jwtProperties;

    @InjectMocks
    private UserSessionService sessionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("user@example.com");
        user.setSecurityVersion(0L);
    }

    @Nested
    @DisplayName("issueTokens")
    class IssueTokensTests {
        @Test
        void issueTokens_createsAccessAndRefreshToken() {
            when(jwtProperties.accessTokenExpiration()).thenReturn(3600L);
            when(jwtProperties.refreshTokenExpiration()).thenReturn(86400L);
            when(authTokenCodec.generateAccessToken(eq("user@example.com"), eq(1L), eq(0L), any()))
                    .thenReturn("access-token");
            when(authTokenCodec.generateRefreshToken(eq("user@example.com"), eq(1L), eq(0L), any(), any(), any()))
                    .thenReturn("refresh-token");
            when(refreshTokenService.hashToken("refresh-token")).thenReturn("hashed-refresh-token");

            TokenResponse response = sessionService.issueTokens(user, List.of("ROLE_USER"), "Desktop", "127.0.0.1");

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isEqualTo("refresh-token");
            assertThat(response.expiresIn()).isEqualTo(3600L);
            verify(refreshTokenService).createRefreshToken(any());
            verify(refreshTokenSessionService).create(any());
        }
    }

    @Nested
    @DisplayName("validateSessionIntegrity")
    class ValidateSessionIntegrityTests {
        @Test
        void validateSessionIntegrity_throwsWhenHashMismatch() {
            RefreshTokenSession session = new RefreshTokenSession(
                    "jti-1", 1L, 0L, "correct-hash", "Desktop", "127.0.0.1",
                    Instant.now(), Instant.now().plusSeconds(3600));
            when(refreshTokenService.hashToken("wrong-token")).thenReturn("wrong-hash");

            assertThatThrownBy(() -> sessionService.validateSessionIntegrity(session, "wrong-token", 0L))
                    .isInstanceOf(RefreshTokenSessionNotFoundException.class);
            verify(refreshTokenService).markRefreshTokenRevoked("wrong-token");
        }
    }

    @Nested
    @DisplayName("blacklistAccessToken")
    class BlacklistTests {
        @Test
        void blacklistAccessToken_blacklistsRemainingSeconds() {
            Instant expiresAt = Instant.now().plusSeconds(600);
            sessionService.blacklistAccessToken("access-token", expiresAt);
            verify(tokenBlacklistService).blacklistToken(eq("access-token"), any(Long.class));
        }
    }
}
