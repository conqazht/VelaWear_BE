package vn.conganh.commercial.feature.auth.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.config.OAuth2Properties;
import vn.conganh.commercial.security.SecurityHmacService;

/**
 * Keeps only an opaque nonce in the browser. The authorization request itself
 * is stored as bounded JSON in Redis and is atomically consumed on callback.
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    static final String REDIS_KEY_PREFIX = "auth:oauth2:v2:request:";

    private static final String COOKIE_PATH = "/";
    private static final String HMAC_DOMAIN = "oauth2-authorization-request";
    private static final String STORE_UNAVAILABLE = "oauth2_authorization_request_store_unavailable";
    private static final String INVALID_REQUEST = "oauth2_authorization_request_invalid";
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int MAX_JSON_LENGTH = 32_768;
    private static final int MAX_MAP_ENTRIES = 64;
    private static final int MAX_COLLECTION_ENTRIES = 64;
    private static final int MAX_VALUE_DEPTH = 4;
    private static final int MAX_STRING_LENGTH = 8_192;
    private static final int NONCE_RESERVATION_ATTEMPTS = 3;

    private final StringRedisTemplate redisTemplate;
    private final SecurityHmacService hmacService;
    private final ObjectMapper objectMapper;
    private final OAuth2Properties oauth2Properties;

    public CookieOAuth2AuthorizationRequestRepository(
            StringRedisTemplate redisTemplate,
            SecurityHmacService hmacService,
            ObjectMapper objectMapper,
            OAuth2Properties oauth2Properties) {
        this.redisTemplate = redisTemplate;
        this.hmacService = hmacService;
        this.objectMapper = objectMapper;
        this.oauth2Properties = oauth2Properties;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Optional<String> nonce = nonceFromCookie(request);
        if (nonce.isEmpty()) {
            return null;
        }
        String payload = loadPayload(redisKey(nonce.get()));
        return payload == null ? null : decode(payload);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String payload = encode(authorizationRequest);
        nonceFromCookie(request).ifPresent(previousNonce ->
                deletePayload(redisKey(previousNonce)));
        String nonce = reservePayload(payload);

        addCookie(
                request,
                response,
                COOKIE_NAME,
                nonce,
                Math.toIntExact(oauth2Properties.authorizationRequestTtl().toSeconds()));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        Optional<String> nonce = nonceFromCookie(request);
        deleteCookie(request, response, COOKIE_NAME);
        if (nonce.isEmpty()) {
            return null;
        }
        String payload = consumePayload(redisKey(nonce.get()));
        return payload == null ? null : decode(payload);
    }

    public void removeAuthorizationRequestCookies(
            HttpServletRequest request,
            HttpServletResponse response) {
        deleteCookie(request, response, COOKIE_NAME);
        nonceFromCookie(request).ifPresent(nonce -> {
            try {
                redisTemplate.delete(redisKey(nonce));
            } catch (DataAccessException | IllegalStateException exception) {
                // Authentication has already reached a terminal handler. The bounded
                // Redis TTL provides cleanup without masking the original outcome.
                log.warn("event=oauth2_authorization_request_cleanup outcome=failure reason=redis_unavailable");
            }
        });
    }

    private String encode(OAuth2AuthorizationRequest request) {
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.equals(request.getGrantType())) {
            throw invalidRequest(null);
        }
        StoredAuthorizationRequest stored = new StoredAuthorizationRequest(
                required(request.getAuthorizationUri()),
                required(request.getClientId()),
                required(request.getRedirectUri()),
                safeScopes(request.getScopes()),
                required(request.getState()),
                safeMap(request.getAdditionalParameters(), 0),
                safeMap(request.getAttributes(), 0),
                required(request.getAuthorizationRequestUri()));
        try {
            String payload = objectMapper.writeValueAsString(stored);
            if (payload.length() > MAX_JSON_LENGTH) {
                throw invalidRequest(null);
            }
            return payload;
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidRequest(exception);
        }
    }

    private OAuth2AuthorizationRequest decode(String payload) {
        if (payload.isBlank() || payload.length() > MAX_JSON_LENGTH) {
            throw invalidRequest(null);
        }
        try {
            StoredAuthorizationRequest stored = objectMapper.readValue(
                    payload,
                    StoredAuthorizationRequest.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(required(stored.authorizationUri()))
                    .clientId(required(stored.clientId()))
                    .redirectUri(required(stored.redirectUri()))
                    .scopes(safeScopes(stored.scopes()))
                    .state(required(stored.state()))
                    .additionalParameters(safeMap(stored.additionalParameters(), 0))
                    .attributes(safeMap(stored.attributes(), 0))
                    .authorizationRequestUri(required(stored.authorizationRequestUri()))
                    .build();
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidRequest(exception);
        }
    }

    private Set<String> safeScopes(Set<String> scopes) {
        if (scopes == null || scopes.size() > MAX_COLLECTION_ENTRIES) {
            throw invalidRequest(null);
        }
        LinkedHashMap<String, Boolean> uniqueScopes = new LinkedHashMap<>();
        for (String scope : scopes) {
            uniqueScopes.put(required(scope), Boolean.TRUE);
        }
        return Collections.unmodifiableSet(uniqueScopes.keySet());
    }

    private Map<String, Object> safeMap(Map<String, Object> source, int depth) {
        if (source == null || source.size() > MAX_MAP_ENTRIES || depth > MAX_VALUE_DEPTH) {
            throw invalidRequest(null);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(required(key), safeValue(value, depth + 1)));
        return Collections.unmodifiableMap(copy);
    }

    private Object safeValue(Object value, int depth) {
        if (depth > MAX_VALUE_DEPTH || value == null) {
            throw invalidRequest(null);
        }
        if (value instanceof String stringValue) {
            return required(stringValue);
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > MAX_COLLECTION_ENTRIES) {
                throw invalidRequest(null);
            }
            List<Object> values = new ArrayList<>(collection.size());
            collection.forEach(item -> values.add(safeValue(item, depth + 1)));
            return Collections.unmodifiableList(values);
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_MAP_ENTRIES) {
                throw invalidRequest(null);
            }
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String stringKey)) {
                    throw invalidRequest(null);
                }
                values.put(required(stringKey), safeValue(item, depth + 1));
            });
            return Collections.unmodifiableMap(values);
        }
        throw invalidRequest(null);
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_STRING_LENGTH) {
            throw invalidRequest(null);
        }
        return value;
    }

    private Optional<String> nonceFromCookie(HttpServletRequest request) {
        return Arrays.stream(request.getCookies() == null ? new Cookie[0] : request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && NONCE_PATTERN.matcher(value).matches())
                .findFirst();
    }

    private String redisKey(String nonce) {
        return REDIS_KEY_PREFIX + hmacService.hash(HMAC_DOMAIN, nonce);
    }

    private String reservePayload(String payload) {
        for (int attempt = 0; attempt < NONCE_RESERVATION_ATTEMPTS; attempt++) {
            String nonce = hmacService.randomToken();
            try {
                Boolean stored = redisTemplate.opsForValue().setIfAbsent(
                        redisKey(nonce),
                        payload,
                        oauth2Properties.authorizationRequestTtl());
                if (Boolean.TRUE.equals(stored)) {
                    return nonce;
                }
                if (stored == null) {
                    throw storeUnavailable(new IllegalStateException(
                            "Redis did not return a nonce reservation result"));
                }
            } catch (DataAccessException | IllegalStateException exception) {
                throw storeUnavailable(exception);
            }
        }
        throw storeUnavailable(new IllegalStateException(
                "Unable to reserve a unique OAuth2 authorization request nonce"));
    }

    private String loadPayload(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException | IllegalStateException exception) {
            throw storeUnavailable(exception);
        }
    }

    private String consumePayload(String key) {
        try {
            return redisTemplate.opsForValue().getAndDelete(key);
        } catch (DataAccessException | IllegalStateException exception) {
            throw storeUnavailable(exception);
        }
    }

    private void deletePayload(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException | IllegalStateException exception) {
            throw storeUnavailable(exception);
        }
    }

    private OAuth2AuthenticationException storeUnavailable(RuntimeException cause) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(STORE_UNAVAILABLE),
                cause);
    }

    private OAuth2AuthenticationException invalidRequest(RuntimeException cause) {
        OAuth2Error error = new OAuth2Error(INVALID_REQUEST);
        return cause == null
                ? new OAuth2AuthenticationException(error)
                : new OAuth2AuthenticationException(error, cause);
    }

    private void addCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds) {
        response.addHeader("Set-Cookie", cookieHeader(request, name, value, maxAgeSeconds));
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        response.addHeader("Set-Cookie", cookieHeader(request, name, "", 0));
    }

    private String cookieHeader(HttpServletRequest request, String name, String value, int maxAgeSeconds) {
        StringBuilder header = new StringBuilder()
                .append(name)
                .append("=")
                .append(value)
                .append("; Path=")
                .append(COOKIE_PATH)
                .append("; HttpOnly; SameSite=Lax; Max-Age=")
                .append(maxAgeSeconds);
        if (request.isSecure()) {
            header.append("; Secure");
        }
        return header.toString();
    }

    private record StoredAuthorizationRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri) {
    }
}
