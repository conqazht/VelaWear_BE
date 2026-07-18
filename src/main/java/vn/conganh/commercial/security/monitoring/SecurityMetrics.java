package vn.conganh.commercial.security.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;

@Component
public class SecurityMetrics {

    private final MeterRegistry meterRegistry;

    public SecurityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void otpRequest(String purpose, String outcome) {
        meterRegistry.counter("security.otp.requests", "purpose", bounded(purpose), "outcome", bounded(outcome))
                .increment();
    }

    public void otpVerification(String purpose, String outcome) {
        meterRegistry.counter("security.otp.verifications", "purpose", bounded(purpose), "outcome", bounded(outcome))
                .increment();
    }

    public void authAttempt(String operation, String outcome) {
        meterRegistry.counter("security.auth.attempts", "operation", bounded(operation), "outcome", bounded(outcome))
                .increment();
    }

    public void rateLimitRejected(String policy, String dimension) {
        meterRegistry.counter(
                        "security.rate.limit.rejections",
                        "policy", bounded(policy),
                        "dimension", bounded(dimension))
                .increment();
    }

    public void sessionsRevoked(String reason, String outcome) {
        meterRegistry.counter(
                        "security.sessions.revoked",
                        "reason", bounded(reason),
                        "outcome", bounded(outcome))
                .increment();
    }

    public void avatarCleanup(String outcome, String reason) {
        meterRegistry.counter(
                        "security.avatar.cleanup",
                        "outcome", bounded(outcome),
                        "reason", bounded(reason))
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopOtpDelivery(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("security.otp.delivery.duration")
                .tag("outcome", bounded(outcome))
                .publishPercentileHistogram(false)
                .minimumExpectedValue(Duration.ofMillis(1))
                .register(meterRegistry));
    }

    public <T> T timeAuthOperation(String operation, Callable<T> call) throws Exception {
        return Timer.builder("security.auth.operation.duration")
                .tag("operation", bounded(operation))
                .register(meterRegistry)
                .recordCallable(call);
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.length() > 64 ? "other" : normalized;
    }
}
