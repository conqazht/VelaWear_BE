package vn.conganh.commercial.security.ratelimit;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.conganh.commercial.exception.CodedBusinessException;

public class RateLimitExceededException extends CodedBusinessException {

    private final long retryAfterSeconds;
    private final String policy;
    private final String dimension;

    public RateLimitExceededException(
            String code,
            String policy,
            String dimension,
            long retryAfterSeconds) {
        super(
                code,
                "Too many requests. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of("retryAfterSeconds", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
        this.policy = policy;
        this.dimension = dimension;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getPolicy() {
        return policy;
    }

    public String getDimension() {
        return dimension;
    }
}
