package vn.conganh.commercial.security.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Map<String, Policy> policies
) {
    public RateLimitProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);
    }

    public record Policy(Map<String, List<Window>> dimensions) {
        public Policy {
            dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
        }
    }

    public record Window(int limit, Duration window) {
        public Window {
            if (limit <= 0) {
                throw new IllegalArgumentException("Rate-limit value must be positive");
            }
            if (window == null || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("Rate-limit window must be positive");
            }
        }
    }
}
