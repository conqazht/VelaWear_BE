package vn.conganh.commercial.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.oauth2")
public record OAuth2Properties(
        String frontendSuccessUrl,
        String frontendFailureUrl,
        long loginCodeTtlSeconds
) {

    public OAuth2Properties {
        if (frontendSuccessUrl == null || frontendSuccessUrl.isBlank()) {
            throw new IllegalArgumentException("OAuth2 frontend success URL is required");
        }
        if (frontendFailureUrl == null || frontendFailureUrl.isBlank()) {
            throw new IllegalArgumentException("OAuth2 frontend failure URL is required");
        }
        if (loginCodeTtlSeconds <= 0) {
            throw new IllegalArgumentException("OAuth2 login code TTL must be greater than zero");
        }
    }

    public Duration loginCodeTtl() {
        return Duration.ofSeconds(loginCodeTtlSeconds);
    }
}
