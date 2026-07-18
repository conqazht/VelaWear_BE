package vn.conganh.commercial.feature.emailoutbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.email")
public record EmailNotificationProperties(
        String storefrontBaseUrl,
        Outbox outbox
) {

    public EmailNotificationProperties {
        if (storefrontBaseUrl == null || storefrontBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Email storefront base URL is required");
        }
        storefrontBaseUrl = stripTrailingSlash(storefrontBaseUrl.trim());
        if (outbox == null) {
            throw new IllegalArgumentException("Email outbox configuration is required");
        }
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    public record Outbox(
            boolean enabled,
            long scanMs,
            int batchSize,
            int maxAttempts,
            Duration leaseDuration,
            Duration retryBaseDelay,
            Duration retryMaxDelay
    ) {

        public Outbox {
            if (scanMs <= 0) {
                throw new IllegalArgumentException("Email outbox scan interval must be positive");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Email outbox batch size must be positive");
            }
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("Email outbox max attempts must be positive");
            }
            requirePositive(leaseDuration, "lease duration");
            requirePositive(retryBaseDelay, "retry base delay");
            requirePositive(retryMaxDelay, "retry max delay");
            if (retryMaxDelay.compareTo(retryBaseDelay) < 0) {
                throw new IllegalArgumentException("Email outbox max retry delay must not be shorter than base delay");
            }
        }

        public Duration retryDelayForAttempt(int attemptCount) {
            int exponent = Math.max(0, Math.min(30, attemptCount - 1));
            try {
                Duration delay = retryBaseDelay.multipliedBy(1L << exponent);
                return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
            } catch (ArithmeticException exception) {
                return retryMaxDelay;
            }
        }

        private static void requirePositive(Duration value, String field) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("Email outbox " + field + " must be positive");
            }
        }
    }
}
