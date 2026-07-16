package vn.conganh.commercial.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TrustedProxyConfigurationValidatorTest {

    @Test
    void nativeStrategy_requiresExplicitNonWildcardProxyRegex() {
        MockEnvironment missing = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE");
        MockEnvironment wildcard = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE")
                .withProperty("server.tomcat.remoteip.internal-proxies", ".*");
        MockEnvironment wrappedWildcard = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE")
                .withProperty("server.tomcat.remoteip.internal-proxies", "(?:.*)");
        MockEnvironment everyIpv4 = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE")
                .withProperty(
                        "server.tomcat.remoteip.internal-proxies",
                        "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}");

        assertThatThrownBy(() -> new TrustedProxyConfigurationValidator(missing).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrustedProxyConfigurationValidator(wildcard).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrustedProxyConfigurationValidator(wrappedWildcard).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TrustedProxyConfigurationValidator(everyIpv4).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitProxyRegex_isAcceptedOnlyWhenNativeIsEnabled() {
        MockEnvironment nativeEnvironment = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE")
                .withProperty("server.tomcat.remoteip.internal-proxies", "10\\.0\\.0\\.12|10\\.0\\.0\\.13");
        MockEnvironment directEnvironment = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NONE");

        assertThatCode(() -> new TrustedProxyConfigurationValidator(nativeEnvironment).validate())
                .doesNotThrowAnyException();
        assertThatCode(() -> new TrustedProxyConfigurationValidator(directEnvironment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void frameworkStrategy_isRejectedBecauseItHasNoTrustedProxyBoundary() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "FRAMEWORK");

        assertThatThrownBy(() -> new TrustedProxyConfigurationValidator(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NONE or NATIVE");
    }
}
