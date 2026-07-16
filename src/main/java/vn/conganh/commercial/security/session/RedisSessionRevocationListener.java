package vn.conganh.commercial.security.session;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenSessionService;
import vn.conganh.commercial.security.monitoring.SecurityEventLogger;
import vn.conganh.commercial.security.monitoring.SecurityMetrics;

@Component
class RedisSessionRevocationListener {

    private final RefreshTokenSessionService refreshTokenSessionService;
    private final SecurityMetrics metrics;
    private final SecurityEventLogger eventLogger;

    RedisSessionRevocationListener(
            RefreshTokenSessionService refreshTokenSessionService,
            SecurityMetrics metrics,
            SecurityEventLogger eventLogger) {
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.metrics = metrics;
        this.eventLogger = eventLogger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void cleanupRedisSessions(SessionsRevokedEvent event) {
        String reason = event.reason().name().toLowerCase(java.util.Locale.ROOT);
        try {
            long removed = refreshTokenSessionService.revokeAll(event.userId());
            metrics.sessionsRevoked(reason, "success");
            eventLogger.event(
                    "sessions_revoked",
                    "success",
                    reason + ":redis_removed=" + Math.min(removed, 9999),
                    event.userId(),
                    null,
                    null);
        } catch (RuntimeException exception) {
            // PostgreSQL securityVersion has already committed, so old tokens
            // remain invalid even if stale Redis keys live until their TTL.
            metrics.sessionsRevoked(reason, "redis_cleanup_failed");
            eventLogger.warning("sessions_revoked", reason + ":redis_cleanup_failed", event.userId());
        }
    }
}
