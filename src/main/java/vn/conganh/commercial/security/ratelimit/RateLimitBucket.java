package vn.conganh.commercial.security.ratelimit;

import java.time.Duration;

record RateLimitBucket(
        String key,
        String policy,
        String dimension,
        int limit,
        Duration window
) {
}
