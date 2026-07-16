package vn.conganh.commercial.security.ratelimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.security.SecurityHmacService;
import vn.conganh.commercial.security.monitoring.SecurityEventLogger;
import vn.conganh.commercial.security.monitoring.SecurityMetrics;

@Service
public class AuthRateLimitService {

    private static final String KEY_PREFIX = "security:rate:v1:";

    private final RateLimitProperties properties;
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final SecurityHmacService hmacService;
    private final SecurityMetrics metrics;
    private final SecurityEventLogger eventLogger;

    public AuthRateLimitService(
            RateLimitProperties properties,
            RedisSlidingWindowRateLimiter rateLimiter,
            SecurityHmacService hmacService,
            SecurityMetrics metrics,
            SecurityEventLogger eventLogger) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.hmacService = hmacService;
        this.metrics = metrics;
        this.eventLogger = eventLogger;
    }

    public void enforce(String policy, String responseCode, Map<String, String> subjects) {
        enforce(policy, responseCode, subjects, subjects.get("ip"));
    }

    public void enforce(
            String policy,
            String responseCode,
            Map<String, String> subjects,
            String clientIpSubject) {
        if (!properties.enabled()) {
            return;
        }
        List<RateLimitBucket> buckets = buckets(policy, subjects);
        try {
            rateLimiter.consume(responseCode, buckets);
        } catch (RateLimitExceededException exception) {
            metrics.rateLimitRejected(exception.getPolicy(), exception.getDimension());
            eventLogger.event(
                    "rate_limit_rejected",
                    "rejected",
                    exception.getPolicy() + ":" + exception.getDimension(),
                    null,
                    null,
                    clientIpHash(clientIpSubject));
            throw exception;
        }
    }

    public void clear(String policy, Map<String, String> subjects) {
        if (!properties.enabled()) {
            return;
        }
        rateLimiter.clear(buckets(policy, subjects));
    }

    public String subjectHash(String domain, String value) {
        return hmacService.hash("rate-limit:" + domain, value);
    }

    private List<RateLimitBucket> buckets(String policyName, Map<String, String> subjects) {
        RateLimitProperties.Policy policy = properties.policies().get(policyName);
        if (policy == null) {
            throw new IllegalStateException("Missing rate-limit policy: " + policyName);
        }
        List<RateLimitBucket> buckets = new ArrayList<>();
        subjects.forEach((dimension, subject) -> {
            List<RateLimitProperties.Window> windows = policy.dimensions().get(dimension);
            if (windows == null || windows.isEmpty()) {
                throw new IllegalStateException(
                        "Missing rate-limit dimension " + dimension + " for policy " + policyName);
            }
            String subjectHash = hmacService.hash(
                    "rate-limit:" + policyName + ":" + dimension,
                    subject);
            for (RateLimitProperties.Window window : windows) {
                String key = KEY_PREFIX
                        + policyName + ":"
                        + dimension + ":"
                        + window.window().toMillis() + ":"
                        + window.limit() + ":"
                        + subjectHash;
                buckets.add(new RateLimitBucket(
                        key,
                        policyName,
                        dimension,
                        window.limit(),
                        window.window()));
            }
        });
        return List.copyOf(buckets);
    }

    private String clientIpHash(String clientIpSubject) {
        if (clientIpSubject == null || clientIpSubject.isBlank()) {
            return null;
        }
        return hmacService.hash("rate-limit:client-ip", clientIpSubject);
    }
}
