package vn.conganh.commercial.security.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void event(
            String event,
            String outcome,
            String reason,
            Long userId,
            String purpose,
            String clientIpHash) {
        log.atInfo()
                .addKeyValue("securityEvent", safe(event))
                .addKeyValue("outcome", safe(outcome))
                .addKeyValue("reason", safe(reason))
                .addKeyValue("userId", userId == null ? "anonymous" : userId)
                .addKeyValue("purpose", safe(purpose))
                .addKeyValue("clientIpHash", safeHash(clientIpHash))
                .log("Security event");
    }

    public void warning(String event, String reason, Long userId) {
        log.atWarn()
                .addKeyValue("securityEvent", safe(event))
                .addKeyValue("outcome", "failure")
                .addKeyValue("reason", safe(reason))
                .addKeyValue("userId", userId == null ? "anonymous" : userId)
                .log("Security event requires attention");
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }

    private String safeHash(String value) {
        // SecurityHmacService emits unpadded Base64URL, not hexadecimal.
        if (value == null || !value.matches("[A-Za-z0-9_-]{32,128}")) {
            return "unknown";
        }
        return value.substring(0, Math.min(value.length(), 24));
    }
}
