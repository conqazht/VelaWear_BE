package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.conganh.commercial.config.JwtConfig;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.SessionRevokedException;
import vn.conganh.commercial.exception.UnauthorizedException;

@DisplayName("Module Auth - AuthTokenCodec")
class AuthTokenCodecTest {

    private static final String SECRET_KEY =
            "0123456789012345678901234567890123456789012345678901234567890123";
    private static final String REFRESH_SECRET_KEY =
            "refresh-secret-key-for-auth-service-tests-0123456789012345678901234";

    private AuthTokenCodec tokenCodec;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET_KEY, REFRESH_SECRET_KEY, 900, 259200);
        JwtConfig jwtConfig = new JwtConfig(jwtProperties);
        tokenCodec = new AuthTokenCodec(
                jwtConfig.jwtEncoder(),
                jwtConfig.refreshJwtEncoder(),
                jwtConfig.refreshJwtDecoder(),
                jwtProperties);
    }

    @Nested
    @DisplayName("Access Token Encoding")
    class AccessTokenEncoding {

        @Test
        @DisplayName("generateAccessToken - tạo access token chứa đúng claims và không rỗng")
        void generateAccessToken_createsValidJwt() {
            String token = tokenCodec.generateAccessToken("user@example.com", 1L, 0L, List.of("ROLE_USER"));

            assertThat(token).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Refresh Token Encoding and Decoding")
    class RefreshTokenEncodingAndDecoding {

        @Test
        @DisplayName("generateRefreshToken and validateAndDecodeRefreshJwt - tạo và decode refresh token thành công")
        void generateAndDecodeRefreshToken_success() {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(259200);
            String jti = "test-jti-123";

            String rawRefreshToken = tokenCodec.generateRefreshToken("user@example.com", 1L, 0L, now, expiresAt, jti);
            Jwt jwt = tokenCodec.validateAndDecodeRefreshJwt(rawRefreshToken);

            assertThat(jwt.getSubject()).isEqualTo("user@example.com");
            assertThat(jwt.getClaimAsString("type")).isEqualTo("refresh");
            assertThat(tokenCodec.requireJti(jwt)).isEqualTo("test-jti-123");
            assertThat(tokenCodec.requireSecurityVersion(jwt)).isEqualTo(0L);
        }

        @Test
        @DisplayName("validateAndDecodeRefreshJwt - ném UnauthorizedException khi raw token không hợp lệ")
        void validateAndDecodeRefreshJwt_invalidToken_throwsUnauthorizedException() {
            assertThatThrownBy(() -> tokenCodec.validateAndDecodeRefreshJwt("invalid-jwt-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Refresh token is invalid");
        }

        @Test
        @DisplayName("requireJti - ném UnauthorizedException khi JTI bị thiếu hoặc rỗng")
        void requireJti_missingOrBlank_throwsUnauthorizedException() {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(259200);

            String rawRefreshToken = tokenCodec.generateRefreshToken("user@example.com", 1L, 0L, now, expiresAt, "");
            Jwt jwt = tokenCodec.validateAndDecodeRefreshJwt(rawRefreshToken);

            assertThatThrownBy(() -> tokenCodec.requireJti(jwt))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Refresh token id is invalid");
        }

        @Test
        @DisplayName("requireSecurityVersion - ném SessionRevokedException khi securityVersion không hợp lệ")
        void requireSecurityVersion_invalidVersion_throwsSessionRevokedException() {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(259200);

            // Create token with negative securityVersion
            String rawRefreshToken = tokenCodec.generateRefreshToken("user@example.com", 1L, -1L, now, expiresAt, "jti");
            Jwt jwt = tokenCodec.validateAndDecodeRefreshJwt(rawRefreshToken);

            assertThatThrownBy(() -> tokenCodec.requireSecurityVersion(jwt))
                    .isInstanceOf(SessionRevokedException.class);
        }
    }
}
