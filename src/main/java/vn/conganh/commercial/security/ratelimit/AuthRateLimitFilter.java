package vn.conganh.commercial.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.security.ClientIpResolver;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
            AuthRateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return route(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        RoutePolicy route = route(request);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ClientIpResolver.ClientIp clientIp = clientIpResolver.resolve(request);
        try {
            rateLimitService.enforce(
                    route.policy(),
                    route.responseCode(),
                    Map.of("ip", clientIp.rateLimitPrefix(), "global", "all"));
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException exception) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(exception.getRetryAfterSeconds()));
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    exception.getCode(),
                    exception.getMessage(),
                    exception.getDetails()));
        } catch (ServiceUnavailableException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String code = route.otp() ? "OTP_SERVICE_UNAVAILABLE" : null;
            objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    null,
                    exception.getMessage(),
                    LocalDateTime.now(),
                    code));
        }
    }

    private RoutePolicy route(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && "/api/v1/auth/otp/request".equals(path)) {
            return new RoutePolicy("otp-request", "OTP_RATE_LIMITED", true);
        }
        if ("POST".equals(method) && "/api/v1/auth/otp/verify".equals(path)) {
            return new RoutePolicy("otp-verify", "OTP_RATE_LIMITED", true);
        }
        if ("POST".equals(method) && "/api/v1/auth/login".equals(path)) {
            return new RoutePolicy("login", "AUTH_RATE_LIMITED", false);
        }
        if ("POST".equals(method)
                && ("/api/v1/auth/register".equals(path)
                || "/api/v1/auth/forgot-password/reset".equals(path))) {
            return new RoutePolicy("register-reset", "AUTH_RATE_LIMITED", false);
        }
        if ("PUT".equals(method)
                && ("/api/v1/auth/me/email".equals(path)
                || "/api/v1/auth/me/password".equals(path))) {
            return new RoutePolicy("sensitive-change", "AUTH_RATE_LIMITED", false);
        }
        if ("POST".equals(method) && "/api/v1/auth/refresh".equals(path)) {
            return new RoutePolicy("refresh", "AUTH_RATE_LIMITED", false);
        }
        if ("POST".equals(method) && "/api/v1/auth/oauth2/exchange".equals(path)) {
            return new RoutePolicy("oauth-exchange", "AUTH_RATE_LIMITED", false);
        }
        if (("GET".equals(method) && "/api/v1/auth/me".equals(path))
                || ("POST".equals(method) && "/api/v1/auth/logout".equals(path))) {
            return new RoutePolicy("auth-session", "AUTH_RATE_LIMITED", false);
        }
        return null;
    }

    private record RoutePolicy(String policy, String responseCode, boolean otp) {
    }
}
