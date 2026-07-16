package vn.conganh.commercial.security.ratelimit;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@Component
@RequiredArgsConstructor
class RedisSlidingWindowRateLimiter {

    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local member = ARGV[2]
            local argIndex = 3
            local retryMillis = 0
            local deniedIndex = 0

            for index, key in ipairs(KEYS) do
                local limit = tonumber(ARGV[argIndex])
                local window = tonumber(ARGV[argIndex + 1])
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
                local count = redis.call('ZCARD', key)
                if count >= limit then
                    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    local candidate = window
                    if oldest[2] then
                        candidate = math.max(1, tonumber(oldest[2]) + window - now)
                    end
                    if candidate > retryMillis then
                        retryMillis = candidate
                    end
                    if deniedIndex == 0 then
                        deniedIndex = index
                    end
                end
                argIndex = argIndex + 2
            end

            if deniedIndex > 0 then
                return 'DENY:' .. deniedIndex .. ':' .. retryMillis
            end

            argIndex = 3
            for index, key in ipairs(KEYS) do
                local window = tonumber(ARGV[argIndex + 1])
                redis.call('ZADD', key, now, member .. ':' .. index)
                redis.call('PEXPIRE', key, window * 2)
                argIndex = argIndex + 2
            end
            return 'ALLOW'
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock = Clock.systemUTC();

    void consume(String responseCode, List<RateLimitBucket> buckets) {
        if (buckets.isEmpty()) {
            return;
        }

        List<String> keys = buckets.stream().map(RateLimitBucket::key).toList();
        List<String> arguments = new ArrayList<>(2 + buckets.size() * 2);
        arguments.add(String.valueOf(clock.millis()));
        arguments.add(UUID.randomUUID().toString());
        for (RateLimitBucket bucket : buckets) {
            arguments.add(String.valueOf(bucket.limit()));
            arguments.add(String.valueOf(bucket.window().toMillis()));
        }

        final String result;
        try {
            result = redisTemplate.execute(CONSUME_SCRIPT, keys, arguments.toArray());
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("Rate-limit store is temporarily unavailable");
        }
        if (result == null) {
            throw new ServiceUnavailableException("Rate-limit store returned no result");
        }
        if ("ALLOW".equals(result)) {
            return;
        }
        if (!result.startsWith("DENY:")) {
            throw new ServiceUnavailableException("Rate-limit store returned an invalid result");
        }

        String[] fields = result.split(":", -1);
        try {
            int deniedIndex = Integer.parseInt(fields[1]) - 1;
            long retryMillis = Long.parseLong(fields[2]);
            RateLimitBucket denied = buckets.get(deniedIndex);
            long retrySeconds = Math.max(1, (retryMillis + 999) / 1000);
            throw new RateLimitExceededException(
                    responseCode,
                    denied.policy(),
                    denied.dimension(),
                    retrySeconds);
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            throw new ServiceUnavailableException("Rate-limit store returned an invalid denial result");
        }
    }

    void clear(List<RateLimitBucket> buckets) {
        if (buckets.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(buckets.stream().map(RateLimitBucket::key).toList());
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("Rate-limit store is temporarily unavailable");
        }
    }
}
