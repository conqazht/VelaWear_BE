package vn.conganh.commercial.security;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private final StringRedisTemplate redisTemplate;
    private final SecurityHmacService hmacService;

    public void blacklistToken(String token, long remainingSeconds) {
        if (remainingSeconds > 0) {
            // Access Token là stateless, nên lưu một khóa blacklist ngắn hạn trong Redis đến khi JWT hết hạn.
            String key = blacklistKey(token);
            redisTemplate.opsForValue().set(key, "true", remainingSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String token) {
        // Nếu Redis có khóa này, token đã logout rõ ràng và không được chấp nhận nữa.
        String key = blacklistKey(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String blacklistKey(String token) {
        return BLACKLIST_KEY_PREFIX + hmacService.hash("access-token-blacklist", token);
    }
}
