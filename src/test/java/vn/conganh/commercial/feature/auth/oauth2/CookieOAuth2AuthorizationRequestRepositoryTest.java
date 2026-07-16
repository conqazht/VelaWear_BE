package vn.conganh.commercial.feature.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository();

    @Test
    void forgedForwardedProtoMustNotMarkCookieSecure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(false);
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.removeAuthorizationRequestCookies(request, response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=0")
                .doesNotContain("; Secure");
    }

    @Test
    void trustedServletSecureStateMarksCookieSecure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.removeAuthorizationRequestCookies(request, response);

        assertThat(response.getHeader("Set-Cookie")).contains("; Secure");
    }
}
