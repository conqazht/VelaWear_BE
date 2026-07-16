package vn.conganh.commercial.feature.refreshtoken;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private static final String ACTIVE_KEY_PREFIX = "auth:refresh:active:";
    private static final String USER_INDEX_KEY_PREFIX = "auth:refresh:user:";
    private static final String FIELD_SEPARATOR = "\n";
    private static final long DESTINATION_EXISTS = -1L;
    private static final long STALE_OR_MISSING = 0L;
    private static final long SUCCESS = 1L;
    private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current and current ~= ARGV[1] then
                return -1
            end
            local indexType = redis.call('TYPE', KEYS[2]).ok
            if indexType ~= 'none' and indexType ~= 'zset' then
                return -2
            end
            if current then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            else
                local created = redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
                if not created then
                    return -1
                end
            end

            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[5])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[3])
            local latest = redis.call('ZRANGE', KEYS[2], -1, -1, 'WITHSCORES')
            if #latest == 2 then
                local remaining = math.ceil(tonumber(latest[2]) - tonumber(ARGV[5]))
                if remaining > 0 then
                    redis.call('PEXPIRE', KEYS[2], remaining)
                else
                    redis.call('DEL', KEYS[2])
                end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ROTATE_IF_CURRENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
                return 0
            end
            local indexType = redis.call('TYPE', KEYS[3]).ok
            if indexType ~= 'none' and indexType ~= 'zset' then
                return -2
            end
            local created = redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3], 'NX')
            if not created then
                return -1
            end

            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[3], ARGV[4])
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[7])
            redis.call('ZADD', KEYS[3], ARGV[6], ARGV[5])
            local latest = redis.call('ZRANGE', KEYS[3], -1, -1, 'WITHSCORES')
            if #latest == 2 then
                local remaining = math.ceil(tonumber(latest[2]) - tonumber(ARGV[7]))
                if remaining > 0 then
                    redis.call('PEXPIRE', KEYS[3], remaining)
                else
                    redis.call('DEL', KEYS[3])
                end
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                return 0
            end

            local separator = string.char(10)
            local first = string.find(current, separator, 1, true)
            local second = first and string.find(current, separator, first + 1, true) or nil
            if not first or not second then
                return -1
            end
            local storedJti = string.sub(current, 1, first - 1)
            local userId = string.sub(current, first + 1, second - 1)
            if storedJti ~= ARGV[2] or not string.match(userId, '^%d+$') then
                return -1
            end

            local userIndexKey = ARGV[1] .. userId
            local indexType = redis.call('TYPE', userIndexKey).ok
            if indexType ~= 'none' and indexType ~= 'zset' then
                return -2
            end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', userIndexKey, ARGV[2])
            redis.call('ZREMRANGEBYSCORE', userIndexKey, '-inf', ARGV[3])
            local latest = redis.call('ZRANGE', userIndexKey, -1, -1, 'WITHSCORES')
            if #latest == 2 then
                local remaining = math.ceil(tonumber(latest[2]) - tonumber(ARGV[3]))
                if remaining > 0 then
                    redis.call('PEXPIRE', userIndexKey, remaining)
                else
                    redis.call('DEL', userIndexKey)
                end
            else
                redis.call('DEL', userIndexKey)
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE_ALL_SCRIPT = new DefaultRedisScript<>("""
            local indexType = redis.call('TYPE', KEYS[1]).ok
            if indexType ~= 'none' and indexType ~= 'zset' then
                return -1
            end
            local members = redis.call('ZRANGE', KEYS[1], 0, -1)
            local deleted = 0
            for _, jti in ipairs(members) do
                deleted = deleted + redis.call('DEL', ARGV[1] .. jti)
            end
            redis.call('DEL', KEYS[1])
            return deleted
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void create(RefreshTokenSession session) {
        Instant now = Instant.now();
        Duration ttl = replacementTtl(session, now);
        Long result = withRedisErrorMapping(() -> redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(activeKey(session.jti()), userIndexKey(session.userId())),
                serialize(session),
                String.valueOf(ttl.toMillis()),
                session.jti(),
                String.valueOf(session.expiresAt().toEpochMilli()),
                String.valueOf(now.toEpochMilli())));
        if (result == null) {
            throw unavailable("Refresh session store returned no create result");
        }
        if (result == DESTINATION_EXISTS) {
            throw new IllegalStateException("Refresh session already exists with different data");
        }
        if (result != SUCCESS) {
            throw unavailable("Refresh session store returned an invalid create result");
        }
    }

    public Optional<RefreshTokenSession> find(String jti) {
        requireText(jti, "Refresh token session JTI is required");
        return withRedisErrorMapping(() -> {
            String raw = redisTemplate.opsForValue().get(activeKey(jti));
            if (raw == null) {
                return Optional.empty();
            }
            RefreshTokenSession session = deserialize(raw);
            validateSessionIdentity(session);
            if (!jti.equals(session.jti())) {
                throw unavailable("Refresh session store contains invalid data");
            }
            return Optional.of(session);
        });
    }

    public void delete(String jti) {
        requireText(jti, "Refresh token session JTI is required");
        Long result = withRedisErrorMapping(() -> redisTemplate.execute(
                DELETE_SCRIPT,
                List.of(activeKey(jti)),
                USER_INDEX_KEY_PREFIX,
                jti,
                String.valueOf(Instant.now().toEpochMilli())));
        if (result == null || result == DESTINATION_EXISTS) {
            throw unavailable("Refresh session store contains invalid data");
        }
        if (result != STALE_OR_MISSING && result != SUCCESS) {
            throw unavailable("Refresh session store returned an invalid delete result");
        }
    }

    public long revokeAll(Long userId) {
        requireUserId(userId);
        Long result = withRedisErrorMapping(() -> redisTemplate.execute(
                REVOKE_ALL_SCRIPT,
                List.of(userIndexKey(userId)),
                ACTIVE_KEY_PREFIX));
        if (result == null || result < 0) {
            throw unavailable("Refresh session store returned an invalid revoke result");
        }
        return result;
    }

    public boolean rotateIfCurrent(
            RefreshTokenSession expectedSession,
            RefreshTokenSession replacementSession) {
        validateSessionIdentity(expectedSession);
        Instant now = Instant.now();
        Duration ttl = replacementTtl(replacementSession, now);
        if (!expectedSession.userId().equals(replacementSession.userId())) {
            throw new IllegalArgumentException("Refresh token rotation cannot change the session user");
        }
        if (expectedSession.securityVersion() != replacementSession.securityVersion()) {
            throw new IllegalArgumentException("Refresh token rotation cannot change the security version");
        }

        Long result = withRedisErrorMapping(() -> redisTemplate.execute(
                ROTATE_IF_CURRENT_SCRIPT,
                List.of(
                        activeKey(expectedSession.jti()),
                        activeKey(replacementSession.jti()),
                        userIndexKey(expectedSession.userId())),
                serialize(expectedSession),
                serialize(replacementSession),
                String.valueOf(ttl.toMillis()),
                expectedSession.jti(),
                replacementSession.jti(),
                String.valueOf(replacementSession.expiresAt().toEpochMilli()),
                String.valueOf(now.toEpochMilli())));
        if (result == null) {
            throw unavailable("Refresh session store returned no rotation result");
        }
        if (result == DESTINATION_EXISTS) {
            throw new IllegalStateException("Replacement refresh session already exists");
        }
        if (result == STALE_OR_MISSING) {
            return false;
        }
        if (result != SUCCESS) {
            throw unavailable("Refresh session store returned an invalid rotation result");
        }
        return true;
    }

    private String activeKey(String jti) {
        return ACTIVE_KEY_PREFIX + jti;
    }

    private String userIndexKey(Long userId) {
        return USER_INDEX_KEY_PREFIX + userId;
    }

    private String serialize(RefreshTokenSession session) {
        return String.join(FIELD_SEPARATOR,
                session.jti(),
                String.valueOf(session.userId()),
                String.valueOf(session.securityVersion()),
                session.tokenHash(),
                encodeNullable(session.deviceInfo()),
                encodeNullable(session.ipAddress()),
                session.issuedAt().toString(),
                session.expiresAt().toString());
    }

    private RefreshTokenSession deserialize(String raw) {
        try {
            String[] fields = raw.split(FIELD_SEPARATOR, -1);
            if (fields.length == 8) {
                return new RefreshTokenSession(
                        fields[0],
                        Long.valueOf(fields[1]),
                        Long.parseLong(fields[2]),
                        fields[3],
                        decodeNullable(fields[4]),
                        decodeNullable(fields[5]),
                        Instant.parse(fields[6]),
                        Instant.parse(fields[7]));
            }
        } catch (RuntimeException exception) {
            throw unavailable("Refresh session store contains invalid data");
        }
        throw unavailable("Refresh session store contains invalid data");
    }

    private String encodeNullable(String value) {
        return value == null ? "" : Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeNullable(String value) {
        return value.isBlank() ? null : new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private Duration replacementTtl(RefreshTokenSession session, Instant now) {
        validateSessionIdentity(session);
        Duration ttl = Duration.between(now, session.expiresAt());
        if (ttl.isNegative() || ttl.isZero() || ttl.toMillis() == 0) {
            throw new IllegalArgumentException("Refresh token session expiry must be in the future");
        }
        return ttl;
    }

    private void validateSessionIdentity(RefreshTokenSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Refresh token session is required");
        }
        requireText(session.jti(), "Refresh token session JTI is required");
        requireText(session.tokenHash(), "Refresh token session hash is required");
        requireUserId(session.userId());
        if (session.securityVersion() < 0) {
            throw new IllegalArgumentException("Refresh token session security version cannot be negative");
        }
        if (session.issuedAt() == null || session.expiresAt() == null
                || !session.expiresAt().isAfter(session.issuedAt())) {
            throw new IllegalArgumentException("Refresh token session timestamps are invalid");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank() || value.contains(FIELD_SEPARATOR)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Refresh token session user ID must be positive");
        }
    }

    private ServiceUnavailableException unavailable(String message) {
        return new ServiceUnavailableException(message);
    }

    private <T> T withRedisErrorMapping(RedisCall<T> call) {
        try {
            return call.execute();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable("Refresh session store is temporarily unavailable");
        }
    }

    @FunctionalInterface
    private interface RedisCall<T> {
        T execute();
    }
}
