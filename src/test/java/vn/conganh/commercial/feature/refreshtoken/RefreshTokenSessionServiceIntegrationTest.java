package vn.conganh.commercial.feature.refreshtoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@DisplayName("Refresh token session Redis index integration")
class RefreshTokenSessionServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTIVE_KEY_PREFIX = "auth:refresh:active:";
    private static final String USER_INDEX_KEY_PREFIX = "auth:refresh:user:";

    @Autowired
    private RefreshTokenSessionService sessionService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void flushRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    @DisplayName("revokeAll chỉ xóa các session thuộc đúng user")
    void revokeAll_multipleUsers_deletesOnlyTargetUserSessions() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        RefreshTokenSession first = session("user-1-a", 1L, 4L, issuedAt, expiresAt);
        RefreshTokenSession second = session("user-1-b", 1L, 4L, issuedAt, expiresAt);
        RefreshTokenSession otherUser = session("user-2-a", 2L, 8L, issuedAt, expiresAt);
        sessionService.create(first);
        sessionService.create(second);
        sessionService.create(otherUser);

        assertThat(indexedJtis(1L)).containsExactlyInAnyOrder(first.jti(), second.jti());
        assertThat(sessionService.revokeAll(1L)).isEqualTo(2);

        assertThat(sessionService.find(first.jti())).isEmpty();
        assertThat(sessionService.find(second.jti())).isEmpty();
        assertThat(redisTemplate.hasKey(USER_INDEX_KEY_PREFIX + 1L)).isFalse();
        assertThat(sessionService.find(otherUser.jti())).contains(otherUser);
        assertThat(indexedJtis(2L)).containsExactly(otherUser.jti());
    }

    @Test
    @DisplayName("rotate chuyển JTI trong user index và delete dọn index cuối cùng")
    void rotateAndDelete_currentSession_updatesUserIndexAtomically() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        RefreshTokenSession current = session("current-jti", 7L, 12L, issuedAt, expiresAt);
        RefreshTokenSession replacement = session("replacement-jti", 7L, 12L, issuedAt, expiresAt);
        sessionService.create(current);

        assertThat(sessionService.rotateIfCurrent(current, replacement)).isTrue();
        assertThat(sessionService.find(current.jti())).isEmpty();
        assertThat(sessionService.find(replacement.jti())).contains(replacement);
        assertThat(indexedJtis(7L)).containsExactly(replacement.jti());

        sessionService.delete(replacement.jti());

        assertThat(sessionService.find(replacement.jti())).isEmpty();
        assertThat(redisTemplate.hasKey(USER_INDEX_KEY_PREFIX + 7L)).isFalse();
    }

    @Test
    @DisplayName("payload Redis cũ bị từ chối để buộc đăng nhập lại")
    void find_legacyPayload_isRejected() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        String legacyJti = "legacy-jti";
        String legacyPayload = String.join("\n",
                legacyJti,
                "9",
                "legacy-hash",
                "",
                "",
                issuedAt.toString(),
                expiresAt.toString());
        redisTemplate.opsForValue().set(
                ACTIVE_KEY_PREFIX + legacyJti,
                legacyPayload,
                Duration.between(Instant.now(), expiresAt));

        assertThatThrownBy(() -> sessionService.find(legacyJti))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    private RefreshTokenSession session(
            String jti,
            Long userId,
            long securityVersion,
            Instant issuedAt,
            Instant expiresAt) {
        return new RefreshTokenSession(
                jti,
                userId,
                securityVersion,
                "hash-" + jti,
                "Chrome",
                "127.0.0.1",
                issuedAt,
                expiresAt);
    }

    private Set<String> indexedJtis(Long userId) {
        Set<String> members = redisTemplate.opsForZSet().range(USER_INDEX_KEY_PREFIX + userId, 0, -1);
        return members == null ? Set.of() : members;
    }
}
