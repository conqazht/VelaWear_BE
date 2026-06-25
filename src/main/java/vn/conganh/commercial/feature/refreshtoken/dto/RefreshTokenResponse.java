package vn.conganh.commercial.feature.refreshtoken.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.refreshtoken.RefreshToken;

public record RefreshTokenResponse(
        Long id,
        Long userId,
        Instant expiresAt,
        boolean revoked,
        String deviceInfo,
        String ipAddress,
        Instant createdAt
) {

    public static RefreshTokenResponse fromEntity(RefreshToken refreshToken) {
        return new RefreshTokenResponse(
                refreshToken.getId(),
                refreshToken.getUser().getId(),
                refreshToken.getExpiresAt(),
                refreshToken.isRevoked(),
                refreshToken.getDeviceInfo(),
                refreshToken.getIpAddress(),
                refreshToken.getCreatedAt());
    }
}
