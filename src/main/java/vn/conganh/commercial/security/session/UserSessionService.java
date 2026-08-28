package vn.conganh.commercial.security.session;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.exception.RefreshTokenSessionNotFoundException;
import vn.conganh.commercial.feature.auth.AuthTokenCodec;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenService;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSession;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
import vn.conganh.commercial.feature.refreshtoken.dto.CreateRefreshTokenRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.security.TokenBlacklistService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final AuthTokenCodec authTokenCodec;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtProperties jwtProperties;

    public TokenResponse issueTokens(User user, List<String> roles, String deviceInfo, String ipAddress) {
        String accessToken = authTokenCodec.generateAccessToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), roles);
        String refreshToken = createRefreshToken(user, deviceInfo, ipAddress);
        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }

    public TokenResponse rotateAndIssueTokens(
            String currentRefreshToken,
            RefreshTokenSession currentSession,
            User user,
            List<String> roles) {
        String newRefreshToken = rotateRefreshToken(currentRefreshToken, user, currentSession);
        String accessToken = authTokenCodec.generateAccessToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), roles);
        return new TokenResponse(accessToken, newRefreshToken, jwtProperties.accessTokenExpiration());
    }

    public Jwt validateRefreshJwt(String rawRefreshToken) {
        return authTokenCodec.validateAndDecodeRefreshJwt(rawRefreshToken);
    }

    public String requireJti(Jwt jwt) {
        return authTokenCodec.requireJti(jwt);
    }

    public long requireSecurityVersion(Jwt jwt) {
        return authTokenCodec.requireSecurityVersion(jwt);
    }

    public Optional<RefreshTokenSession> findSession(String jti) {
        return refreshTokenSessionService.find(jti);
    }

    public void validateSessionIntegrity(
            RefreshTokenSession session,
            String rawRefreshToken,
            long tokenSecurityVersion) {
        String currentTokenHash = refreshTokenService.hashToken(rawRefreshToken);
        if (!currentTokenHash.equals(session.tokenHash())
                || session.securityVersion() != tokenSecurityVersion) {
            refreshTokenService.markRefreshTokenRevoked(rawRefreshToken);
            throw new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
        }
    }

    public void markRevoked(String rawRefreshToken) {
        refreshTokenService.markRefreshTokenRevoked(rawRefreshToken);
    }

    public void deleteSession(String jti) {
        refreshTokenSessionService.delete(jti);
    }

    public void blacklistAccessToken(String tokenValue, Instant expiresAt) {
        if (tokenValue != null && expiresAt != null) {
            long remainingSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            if (remainingSeconds > 0) {
                tokenBlacklistService.blacklistToken(tokenValue, remainingSeconds);
            }
        }
    }

    private String createRefreshToken(User user, String deviceInfo, String ipAddress) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();
        String refreshToken = authTokenCodec.generateRefreshToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), now, expiresAt, jti);
        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                refreshToken,
                expiresAt,
                deviceInfo,
                ipAddress));
        refreshTokenSessionService.create(new RefreshTokenSession(
                jti,
                user.getId(),
                user.getSecurityVersion(),
                refreshTokenService.hashToken(refreshToken),
                deviceInfo,
                ipAddress,
                now,
                expiresAt));
        return refreshToken;
    }

    private String rotateRefreshToken(
            String currentRefreshToken,
            User user,
            RefreshTokenSession currentSession) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration(), ChronoUnit.SECONDS);
        String newJti = UUID.randomUUID().toString();
        String newRefreshToken = authTokenCodec.generateRefreshToken(
                user.getEmail(), user.getId(), user.getSecurityVersion(), now, expiresAt, newJti);
        refreshTokenService.markRefreshTokenRevoked(currentRefreshToken);
        refreshTokenService.createRefreshToken(new CreateRefreshTokenRequest(
                user.getId(),
                newRefreshToken,
                expiresAt,
                currentSession.deviceInfo(),
                currentSession.ipAddress()));
        RefreshTokenSession replacementSession = new RefreshTokenSession(
                newJti,
                user.getId(),
                currentSession.securityVersion(),
                refreshTokenService.hashToken(newRefreshToken),
                currentSession.deviceInfo(),
                currentSession.ipAddress(),
                now,
                expiresAt);
        if (!refreshTokenSessionService.rotateIfCurrent(currentSession, replacementSession)) {
            throw new RefreshTokenSessionNotFoundException("Refresh session is expired or revoked");
        }
        return newRefreshToken;
    }
}
