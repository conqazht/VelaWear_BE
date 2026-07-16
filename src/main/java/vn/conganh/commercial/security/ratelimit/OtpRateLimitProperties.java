package vn.conganh.commercial.security.ratelimit;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.rate-limit.otp")
public class OtpRateLimitProperties {

    private Duration requestCooldown = Duration.ofSeconds(60);
    private int maxAttempts = 5;
    private Duration attemptsLock = Duration.ofMinutes(10);

    @PostConstruct
    void validate() {
        if (requestCooldown == null || requestCooldown.isZero() || requestCooldown.isNegative()) {
            throw new IllegalStateException("OTP request cooldown must be positive");
        }
        if (maxAttempts <= 0) {
            throw new IllegalStateException("OTP max attempts must be positive");
        }
        if (attemptsLock == null || attemptsLock.isZero() || attemptsLock.isNegative()) {
            throw new IllegalStateException("OTP attempts lock must be positive");
        }
    }
}
