package vn.conganh.commercial.feature.refreshtoken;

import java.time.Instant;

public record RefreshTokenSession(
        String jti,
        Long userId,
        long securityVersion,
        String tokenHash,
        String deviceInfo,
        String ipAddress,
        Instant issuedAt,
        Instant expiresAt
) {

    public RefreshTokenSession(
            String jti,
            Long userId,
            String tokenHash,
            String deviceInfo,
            String ipAddress,
            Instant issuedAt,
            Instant expiresAt) {
        this(jti, userId, 0L, tokenHash, deviceInfo, ipAddress, issuedAt, expiresAt);
    }
}
