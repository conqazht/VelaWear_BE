package vn.conganh.commercial.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class TrustedProxyConfigurationValidator {

    private static final Set<String> UNSAFE_PATTERNS = Set.of("", ".*", "^.*$", ".+", "^.+$");
    private static final String SAFE_SENTINEL = "(?!)";
    private static final Set<String> IPV4_TRUST_ALL_PROBES = Set.of(
            "1.1.1.1", "8.8.8.8", "10.37.91.4", "172.19.8.3", "192.168.47.12", "203.0.113.99");
    private static final Set<String> IPV6_TRUST_ALL_PROBES = Set.of(
            "::1", "2001:4860:4860::8888", "2001:db8:85a3::8a2e:370:7334", "fd12:3456:789a::1");

    private final Environment environment;

    public TrustedProxyConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String strategy = environment.getProperty("server.forward-headers-strategy", "NONE")
                .trim()
                .toUpperCase(Locale.ROOT);
        if ("NONE".equals(strategy)) {
            return;
        }
        if (!"NATIVE".equals(strategy)) {
            throw new IllegalStateException(
                    "Forwarded headers strategy must be NONE or NATIVE with an explicit trusted proxy regex");
        }

        String trustedProxyPattern = environment.getProperty(
                "server.tomcat.remoteip.internal-proxies",
                SAFE_SENTINEL).trim();
        if (SAFE_SENTINEL.equals(trustedProxyPattern)
                || UNSAFE_PATTERNS.contains(trustedProxyPattern)
                || trustsEveryAddressInEitherFamily(trustedProxyPattern)) {
            throw new IllegalStateException(
                    "NATIVE forwarded headers require an explicit, non-wildcard trusted proxy regex");
        }
    }

    private boolean trustsEveryAddressInEitherFamily(String trustedProxyPattern) {
        try {
            Pattern pattern = Pattern.compile(trustedProxyPattern);
            return IPV4_TRUST_ALL_PROBES.stream().allMatch(address -> pattern.matcher(address).matches())
                    || IPV6_TRUST_ALL_PROBES.stream().allMatch(address -> pattern.matcher(address).matches());
        } catch (PatternSyntaxException exception) {
            throw new IllegalStateException("Trusted proxy regex is invalid", exception);
        }
    }
}
