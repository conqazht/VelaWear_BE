package vn.conganh.commercial.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    void resolve_ignoresForgedForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("Forwarded", "for=198.51.100.10;proto=https");
        request.addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.11");

        ClientIpResolver.ClientIp result = resolver.resolve(request);

        assertThat(result.address()).isEqualTo("203.0.113.9");
        assertThat(result.rateLimitPrefix()).isEqualTo("203.0.113.9/32");
    }

    @Test
    void normalize_groupsIpv6By64Prefix() {
        ClientIpResolver.ClientIp first = resolver.normalize("2001:db8:abcd:12::1");
        ClientIpResolver.ClientIp second = resolver.normalize("2001:db8:abcd:12:ffff::9");
        ClientIpResolver.ClientIp otherNetwork = resolver.normalize("2001:db8:abcd:13::1");

        assertThat(first.address()).isNotEqualTo(second.address());
        assertThat(first.rateLimitPrefix()).isEqualTo(second.rateLimitPrefix());
        assertThat(first.rateLimitPrefix()).endsWith("/64");
        assertThat(otherNetwork.rateLimitPrefix()).isNotEqualTo(first.rateLimitPrefix());
    }

    @Test
    void normalize_invalidAddress_returnsUnknownWithoutDnsLookup() {
        assertThat(resolver.normalize("attacker.example")).isEqualTo(ClientIpResolver.ClientIp.unknown());
        assertThat(resolver.normalize("999.1.2.3")).isEqualTo(ClientIpResolver.ClientIp.unknown());
    }
}
