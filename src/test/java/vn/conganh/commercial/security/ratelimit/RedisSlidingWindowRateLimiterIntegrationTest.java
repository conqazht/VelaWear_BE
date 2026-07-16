package vn.conganh.commercial.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.conganh.commercial.AbstractIntegrationTest;

class RedisSlidingWindowRateLimiterIntegrationTest extends AbstractIntegrationTest {

    private static final String KEY_PREFIX = "test:security:rate:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisSlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void cleanKeys() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void consume_deniedRequestDoesNotPartiallyConsumeOtherDimensions() {
        RateLimitBucket saturated = bucket("saturated", "ip", 1);
        RateLimitBucket untouched = bucket("untouched", "global", 10);
        rateLimiter.consume("AUTH_RATE_LIMITED", List.of(saturated));

        assertThatThrownBy(() -> rateLimiter.consume(
                        "AUTH_RATE_LIMITED", List.of(saturated, untouched)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("AUTH_RATE_LIMITED");
                    assertThat(exception.getRetryAfterSeconds()).isPositive();
                    assertThat(exception.getDetails()).containsOnlyKeys("retryAfterSeconds");
                });

        assertThat(redisTemplate.opsForZSet().size(untouched.key())).isZero();
    }

    @Test
    void consume_concurrentRequests_allowsExactlyConfiguredLimit() throws Exception {
        RateLimitBucket bucket = bucket("concurrent", "ip", 5);
        int workers = 20;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        rateLimiter.consume("AUTH_RATE_LIMITED", List.of(bucket));
                        return true;
                    } catch (RateLimitExceededException exception) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();

            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
            assertThat(redisTemplate.opsForZSet().size(bucket.key())).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    private RateLimitBucket bucket(String suffix, String dimension, int limit) {
        return new RateLimitBucket(
                KEY_PREFIX + suffix,
                "test-policy",
                dimension,
                limit,
                Duration.ofMinutes(5));
    }
}
