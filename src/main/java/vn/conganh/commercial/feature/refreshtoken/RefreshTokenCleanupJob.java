package vn.conganh.commercial.feature.refreshtoken;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.refresh-token.cleanup-cron:0 0 2 * * *}")
    public void cleanupExpiredAndRevokedTokens() {
        // Mặc định chạy lúc 02:00, thường là giờ ít tải, để bảng refresh_tokens không phình mãi.
        log.info("[VelaWear/Scheduled] Starting cleanup of expired and revoked refresh tokens");
        try {
            int deletedCount = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
            log.info("[VelaWear/Scheduled] Cleanup complete. Deleted {} tokens", deletedCount);
        } catch (Exception e) {
            log.error("[VelaWear/Scheduled] Failed to clean up expired/revoked refresh tokens", e);
        }
    }
}
