package vn.conganh.commercial.feature.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private static final String KEY_PREFIX = "auth:refresh:active:";
    private static final String FIELD_SEPARATOR = "\n";
    private static final long ROTATION_DESTINATION_EXISTS = -1L;
    private static final long ROTATION_STALE = 0L;
    private static final long ROTATION_SUCCESS = 1L;
    private static final DefaultRedisScript<Long> ROTATE_IF_CURRENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            local created = redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3], 'NX')
            if not created then
                return -1
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void create(RefreshTokenSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Refresh token session expiry must be in the future");
        }
        withRedisErrorMapping(() -> {
            redisTemplate.opsForValue().set(key(session.jti()), serialize(session), ttl);
            return null;
        });
    }

    public Optional<RefreshTokenSession> find(String jti) {
        return withRedisErrorMapping(() -> {
            String raw = redisTemplate.opsForValue().get(key(jti));
            return raw == null ? Optional.empty() : Optional.of(deserialize(raw));
        });
    }

    public void delete(String jti) {
        withRedisErrorMapping(() -> {
            redisTemplate.delete(key(jti));
            return null;
        });
    }

    public boolean rotateIfCurrent(
            RefreshTokenSession expectedSession,
            RefreshTokenSession replacementSession) {
        Duration ttl = Duration.between(Instant.now(), replacementSession.expiresAt());
        if (ttl.isNegative() || ttl.isZero() || ttl.toMillis() == 0) {
            throw new IllegalArgumentException("Refresh token session expiry must be in the future");
        }

        Long result = withRedisErrorMapping(() -> redisTemplate.execute(
                ROTATE_IF_CURRENT_SCRIPT,
                List.of(key(expectedSession.jti()), key(replacementSession.jti())),
                serialize(expectedSession),
                serialize(replacementSession),
                String.valueOf(ttl.toMillis())));
        if (result == null) {
            throw new ServiceUnavailableException("Refresh session store is temporarily unavailable");
        }
        if (result == ROTATION_DESTINATION_EXISTS) {
            throw new IllegalStateException("Replacement refresh session already exists");
        }
        if (result == ROTATION_STALE) {
            return false;
        }
        if (result != ROTATION_SUCCESS) {
            throw new ServiceUnavailableException("Refresh session store returned an invalid rotation result");
        }
        return true;
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }

    private String serialize(RefreshTokenSession session) {
        return String.join(FIELD_SEPARATOR,
                session.jti(),
                String.valueOf(session.userId()),
                session.tokenHash(),
                encodeNullable(session.deviceInfo()),
                encodeNullable(session.ipAddress()),
                session.issuedAt().toString(),
                session.expiresAt().toString());
    }

    private RefreshTokenSession deserialize(String raw) {
        String[] fields = raw.split(FIELD_SEPARATOR, -1);
        if (fields.length != 7) {
            throw new ServiceUnavailableException("Refresh session store contains invalid data");
        }
        return new RefreshTokenSession(
                fields[0],
                Long.valueOf(fields[1]),
                fields[2],
                decodeNullable(fields[3]),
                decodeNullable(fields[4]),
                Instant.parse(fields[5]),
                Instant.parse(fields[6]));
    }

    private String encodeNullable(String value) {
        return value == null ? "" : Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeNullable(String value) {
        return value.isBlank() ? null : new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private <T> T withRedisErrorMapping(RedisCall<T> call) {
        try {
            return call.execute();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("Refresh session store is temporarily unavailable");
        }
    }

    @FunctionalInterface
    private interface RedisCall<T> {
        T execute();
    }
}
