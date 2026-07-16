package vn.conganh.commercial.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Produces non-reversible, domain-separated identifiers for sensitive values stored in Redis.
 *
 * <p>The same raw value must never be hashed for two different purposes without a different
 * domain. This prevents (for example) an email limiter key from being confused with an OTP
 * scope key.</p>
 */
@Slf4j
@Component
public class SecurityHmacService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final int RANDOM_TOKEN_BYTES = 32;

    private final byte[] secret;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecurityHmacService(Environment environment) {
        String configuredSecret = environment.getProperty("app.security.hmac-secret");
        if (configuredSecret == null || configuredSecret.isBlank()) {
            configuredSecret = environment.getProperty("SECURITY_HMAC_SECRET");
        }

        boolean production = Set.of(environment.getActiveProfiles()).contains("prod");
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (production) {
                throw new IllegalStateException(
                        "SECURITY_HMAC_SECRET (app.security.hmac-secret) is required in production");
            }
            this.secret = randomBytes(MINIMUM_SECRET_BYTES);
            log.warn("SECURITY_HMAC_SECRET is not configured; using an ephemeral secret outside production");
            return;
        }

        byte[] configuredBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (configuredBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("SECURITY_HMAC_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (configuredSecret.equals(environment.getProperty("jwt.access-token-secret-key"))
                || configuredSecret.equals(environment.getProperty("jwt.refresh-token-secret-key"))) {
            throw new IllegalStateException("SECURITY_HMAC_SECRET must not reuse a JWT signing key");
        }
        this.secret = configuredBytes.clone();
    }

    public String hash(String domain, String value) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("HMAC domain must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("HMAC value must not be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(domain.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    public boolean matches(String expectedDigest, String domain, String rawValue) {
        if (expectedDigest == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedDigest.getBytes(StandardCharsets.US_ASCII),
                hash(domain, rawValue).getBytes(StandardCharsets.US_ASCII));
    }

    public String randomToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(RANDOM_TOKEN_BYTES));
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}
