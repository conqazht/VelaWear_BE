package vn.conganh.commercial.security;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.config.JwtProperties;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";
    private static final String ROLE_UPDATE_KEY_PREFIX = "auth:role-updated-at:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public void blacklistToken(String token, long remainingSeconds) {
        if (remainingSeconds > 0) {
            // Access Token là stateless, nên lưu một khóa blacklist ngắn hạn trong Redis đến khi JWT hết hạn.
            String key = BLACKLIST_KEY_PREFIX + token;
            redisTemplate.opsForValue().set(key, "true", remainingSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String token) {
        // Nếu Redis có khóa này, token đã logout rõ ràng và không được chấp nhận nữa.
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setRoleUpdateTimestamp(Long userId) {
        String key = ROLE_UPDATE_KEY_PREFIX + userId;
        long currentEpochMillis = Instant.now().toEpochMilli();
        // Giữ mốc này ít nhất bằng vòng đời Access Token để token cũ không hợp lệ trở lại.
        long ttl = jwtProperties.accessTokenExpiration();
        redisTemplate.opsForValue().set(key, String.valueOf(currentEpochMillis), ttl, TimeUnit.SECONDS);
    }

    public Long getRoleUpdateTimestamp(Long userId) {
        // Không có khóa nghĩa là chưa ghi nhận thay đổi role gần đây cho user này.
        String key = ROLE_UPDATE_KEY_PREFIX + userId;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : null;
    }
}
