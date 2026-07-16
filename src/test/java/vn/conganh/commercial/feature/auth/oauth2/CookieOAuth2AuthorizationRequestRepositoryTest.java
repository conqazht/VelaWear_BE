package vn.conganh.commercial.feature.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import vn.conganh.commercial.AbstractIntegrationTest;

class CookieOAuth2AuthorizationRequestRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CookieOAuth2AuthorizationRequestRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void saveStoresOnlyOpaqueNonceAndAtomicallyConsumesJsonState() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                saveResponse);

        String nonce = cookieValue(saveResponse.getHeader("Set-Cookie"));
        assertThat(nonce).matches("[A-Za-z0-9_-]{43}");
        assertThat(saveResponse.getHeader("Set-Cookie"))
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=180")
                .doesNotContain("; Secure");

        Set<String> keys = redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(key).doesNotContain(nonce);
        String payload = redisTemplate.opsForValue().get(key);
        assertThat(payload)
                .startsWith("{")
                .contains("\"clientId\":\"google-client\"")
                .doesNotContain("rO0AB");
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isBetween(170_000L, 180_000L);

        MockHttpServletRequest callback = callback(nonce);
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(callback);
        assertRoundTrip(loaded);

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest consumed = repository.removeAuthorizationRequest(
                callback,
                removeResponse);
        assertRoundTrip(consumed);
        assertThat(removeResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(repository.removeAuthorizationRequest(
                callback,
                new MockHttpServletResponse())).isNull();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void concurrentCallbacksAllowExactlyOneConsumer() throws Exception {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                saveResponse);
        String nonce = cookieValue(saveResponse.getHeader("Set-Cookie"));

        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                tasks.add(executor.submit(() -> {
                    await(start);
                    OAuth2AuthorizationRequest result = repository.removeAuthorizationRequest(
                            callback(nonce),
                            new MockHttpServletResponse());
                    if (result != null) {
                        winners.incrementAndGet();
                    }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(winners).hasValue(1);
        assertThat(redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void malformedCookieIsRejectedWithoutTouchingRedis() {
        MockHttpServletRequest request = callback("not-a-valid-nonce!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(repository.removeAuthorizationRequest(request, response)).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void legacyJavaSerializedCookieIsRejectedWithoutDeserialization() {
        MockHttpServletRequest request = callback(
                "rO0ABXNyAE9vcmcuc3ByaW5nZnJhbWV3b3JrLnNlY3VyaXR5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(repository.removeAuthorizationRequest(request, response)).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void startingNewLoginInvalidatesPreviousAuthorizationRequest() {
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                firstResponse);
        String firstNonce = cookieValue(firstResponse.getHeader("Set-Cookie"));

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                callback(firstNonce),
                secondResponse);
        String secondNonce = cookieValue(secondResponse.getHeader("Set-Cookie"));

        assertThat(secondNonce).isNotEqualTo(firstNonce);
        assertThat(repository.loadAuthorizationRequest(callback(firstNonce))).isNull();
        assertRoundTrip(repository.loadAuthorizationRequest(callback(secondNonce)));
        assertThat(redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")).hasSize(1);
    }

    @Test
    void savingNullAuthorizationRequestConsumesExistingFlowAndDeletesCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                saveResponse);
        String nonce = cookieValue(saveResponse.getHeader("Set-Cookie"));
        MockHttpServletResponse cleanupResponse = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(null, callback(nonce), cleanupResponse);

        assertThat(cleanupResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
        assertThat(repository.loadAuthorizationRequest(callback(nonce))).isNull();
        assertThat(redisTemplate.keys(
                CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    void malformedRedisJsonIsConsumedAndRejected() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                saveResponse);
        String nonce = cookieValue(saveResponse.getHeader("Set-Cookie"));
        String key = redisTemplate.keys(
                        CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")
                .iterator()
                .next();
        redisTemplate.opsForValue().set(key, "rO0AB-not-json", Duration.ofMinutes(3));

        assertThatThrownBy(() -> repository.removeAuthorizationRequest(
                        callback(nonce),
                        new MockHttpServletResponse()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode())
                .isEqualTo("oauth2_authorization_request_invalid");
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    void oversizedRedisJsonIsConsumedAndRejected() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(),
                new MockHttpServletRequest(),
                saveResponse);
        String nonce = cookieValue(saveResponse.getHeader("Set-Cookie"));
        String key = redisTemplate.keys(
                        CookieOAuth2AuthorizationRequestRepository.REDIS_KEY_PREFIX + "*")
                .iterator()
                .next();
        redisTemplate.opsForValue().set(key, "x".repeat(32_769), Duration.ofMinutes(3));

        assertThatThrownBy(() -> repository.removeAuthorizationRequest(
                        callback(nonce),
                        new MockHttpServletResponse()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception)
                        .getError().getErrorCode())
                .isEqualTo("oauth2_authorization_request_invalid");
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

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

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("google-client")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scopes(Set.of("openid", "email", "profile"))
                .state("state-value")
                .additionalParameters(Map.of(
                        "nonce", "oidc-nonce",
                        "prompt", "select_account",
                        "code_challenge", "pkce-challenge",
                        "code_challenge_method", "S256"))
                .attributes(Map.of(
                        "registration_id", "google",
                        "code_verifier", "pkce-verifier",
                        "nonce", "raw-oidc-nonce"))
                .authorizationRequestUri(
                        "https://accounts.google.com/o/oauth2/v2/auth?client_id=google-client&state=state-value")
                .build();
    }

    private void assertRoundTrip(OAuth2AuthorizationRequest request) {
        assertThat(request).isNotNull();
        assertThat(request.getAuthorizationUri())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth");
        assertThat(request.getClientId()).isEqualTo("google-client");
        assertThat(request.getRedirectUri())
                .isEqualTo("http://localhost:8080/login/oauth2/code/google");
        assertThat(request.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
        assertThat(request.getState()).isEqualTo("state-value");
        assertThat(request.getAdditionalParameters())
                .containsEntry("nonce", "oidc-nonce")
                .containsEntry("prompt", "select_account")
                .containsEntry("code_challenge", "pkce-challenge")
                .containsEntry("code_challenge_method", "S256");
        assertThat(request.getAttributes())
                .containsEntry("registration_id", "google")
                .containsEntry("code_verifier", "pkce-verifier")
                .containsEntry("nonce", "raw-oidc-nonce");
    }

    private MockHttpServletRequest callback(String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
                nonce));
        return request;
    }

    private String cookieValue(String setCookie) {
        assertThat(setCookie).isNotNull();
        int valueStart = setCookie.indexOf('=') + 1;
        return setCookie.substring(valueStart, setCookie.indexOf(';', valueStart));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent callback start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", exception);
        }
    }
}
