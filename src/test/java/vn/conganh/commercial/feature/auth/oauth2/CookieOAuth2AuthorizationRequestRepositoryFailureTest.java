package vn.conganh.commercial.feature.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.config.OAuth2Properties;
import vn.conganh.commercial.security.SecurityHmacService;

@ExtendWith(MockitoExtension.class)
class CookieOAuth2AuthorizationRequestRepositoryFailureTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SecurityHmacService hmacService;

    private CookieOAuth2AuthorizationRequestRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CookieOAuth2AuthorizationRequestRepository(
                redisTemplate,
                hmacService,
                new ObjectMapper(),
                new OAuth2Properties(
                        "http://localhost:3000/auth/oauth2/callback",
                        "http://localhost:3000/sign-in",
                        120,
                        180));
    }

    @Test
    void redisFailureFailsClosedWithStableOAuthError() {
        String nonce = "A".repeat(43);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                nonce));
        when(hmacService.hash("oauth2-authorization-request", nonce)).thenReturn("digest");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThatThrownBy(() -> repository.loadAuthorizationRequest(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode())
                .isEqualTo("oauth2_authorization_request_store_unavailable");
    }

    @Test
    void callbackRedisFailureDeletesCookieAndFailsClosed() {
        String nonce = "A".repeat(43);
        MockHttpServletRequest request = callback(nonce);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(hmacService.hash("oauth2-authorization-request", nonce)).thenReturn("digest");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete(anyString()))
                .thenThrow(new IllegalStateException("Redis unavailable"));

        assertThatThrownBy(() -> repository.removeAuthorizationRequest(request, response))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode())
                .isEqualTo("oauth2_authorization_request_store_unavailable");
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void initiationRedisFailureDoesNotIssueBrowserCookie() {
        String nonce = "A".repeat(43);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(hmacService.randomToken()).thenReturn(nonce);
        when(hmacService.hash("oauth2-authorization-request", nonce)).thenReturn("digest");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThatThrownBy(() -> repository.saveAuthorizationRequest(
                        authorizationRequest(),
                        new MockHttpServletRequest(),
                        response))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode())
                .isEqualTo("oauth2_authorization_request_store_unavailable");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void nonceCollisionRetriesWithoutOverwritingExistingState() {
        String firstNonce = "A".repeat(43);
        String secondNonce = "B".repeat(43);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(hmacService.randomToken()).thenReturn(firstNonce, secondNonce);
        when(hmacService.hash("oauth2-authorization-request", firstNonce))
                .thenReturn("first-digest");
        when(hmacService.hash("oauth2-authorization-request", secondNonce))
                .thenReturn("second-digest");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false, true);

        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME
                        + "=" + secondNonce);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    private MockHttpServletRequest callback(String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                nonce));
        return request;
    }

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("google-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("openid", "email"))
                .state("state-value")
                .additionalParameters(Map.of("nonce", "oidc-nonce"))
                .attributes(Map.of("registration_id", "google"))
                .authorizationRequestUri(
                        "https://accounts.google.com/o/oauth2/v2/auth?client_id=google-client")
                .build();
    }
}
