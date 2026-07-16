package vn.conganh.commercial.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecurityHmacServiceTest {

    private static final String SECRET =
            "security-hmac-secret-that-is-longer-than-thirty-two-bytes";

    @Test
    void hash_isDeterministicAndDomainSeparated() {
        SecurityHmacService service = new SecurityHmacService(
                new MockEnvironment().withProperty("app.security.hmac-secret", SECRET));

        assertThat(service.hash("otp-proof", "same-value"))
                .isEqualTo(service.hash("otp-proof", "same-value"))
                .isNotEqualTo(service.hash("otp-code", "same-value"));
        assertThat(service.randomToken()).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void constructor_rejectsShortMissingProductionOrReusedJwtSecret() {
        assertThatThrownBy(() -> new SecurityHmacService(
                        new MockEnvironment().withProperty("app.security.hmac-secret", "too-short")))
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment production = new MockEnvironment();
        production.setActiveProfiles("prod");
        assertThatThrownBy(() -> new SecurityHmacService(production))
                .isInstanceOf(IllegalStateException.class);

        MockEnvironment reused = new MockEnvironment()
                .withProperty("app.security.hmac-secret", SECRET)
                .withProperty("jwt.access-token-secret-key", SECRET);
        assertThatThrownBy(() -> new SecurityHmacService(reused))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not reuse");
    }
}
