package vn.conganh.commercial.feature.auth.oauth2;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.config.OAuth2Properties;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.exception.UnauthorizedException;

@Service
@RequiredArgsConstructor
public class OAuth2LoginCodeService {

    private static final String KEY_PREFIX = "auth:oauth2:login-code:";

    private final StringRedisTemplate redisTemplate;
    private final OAuth2Properties oauth2Properties;

    public String create(Long userId) {
        String code = UUID.randomUUID().toString();
        withRedisErrorMapping(() -> {
            redisTemplate.opsForValue().set(key(code), String.valueOf(userId), oauth2Properties.loginCodeTtl());
            return null;
        });
        return code;
    }

    public Long consume(String code) {
        if (code == null || code.isBlank()) {
            throw new UnauthorizedException("OAuth2 login code is required");
        }
        String rawUserId = withRedisErrorMapping(() -> redisTemplate.opsForValue().getAndDelete(key(code)));
        if (rawUserId == null || rawUserId.isBlank()) {
            throw new UnauthorizedException("OAuth2 login code is expired or invalid");
        }
        try {
            return Long.valueOf(rawUserId);
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("OAuth2 login code is invalid");
        }
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }

    private <T> T withRedisErrorMapping(RedisCall<T> call) {
        try {
            return call.execute();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RedisSystemException | org.springframework.data.redis.RedisConnectionFailureException
                 | QueryTimeoutException exception) {
            throw new ServiceUnavailableException("OAuth2 login code store is temporarily unavailable");
        }
    }

    @FunctionalInterface
    private interface RedisCall<T> {
        T execute();
    }
}
