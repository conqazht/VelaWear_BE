package vn.conganh.commercial.feature.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private static final String KEY_PREFIX = "auth:refresh:active:";
    private static final String FIELD_SEPARATOR = "\n";

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

    public void rotate(String oldJti, RefreshTokenSession newSession) {
        withRedisErrorMapping(() -> {
            redisTemplate.delete(key(oldJti));
            Duration ttl = Duration.between(Instant.now(), newSession.expiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("Refresh token session expiry must be in the future");
            }
            redisTemplate.opsForValue().set(key(newSession.jti()), serialize(newSession), ttl);
            return null;
        });
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
        } catch (RedisSystemException | org.springframework.data.redis.RedisConnectionFailureException
                 | org.springframework.dao.QueryTimeoutException exception) {
            throw new ServiceUnavailableException("Refresh session store is temporarily unavailable");
        }
    }

    @FunctionalInterface
    private interface RedisCall<T> {
        T execute();
    }
}
