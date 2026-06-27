package vn.conganh.commercial.feature.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.config.JwtProperties;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.exception.UnauthorizedException;
import vn.conganh.commercial.feature.auth.dto.LoginRequest;
import vn.conganh.commercial.feature.auth.dto.RefreshTokenRequest;
import vn.conganh.commercial.feature.auth.dto.RegisterRequest;
import vn.conganh.commercial.feature.auth.dto.TokenResponse;
import vn.conganh.commercial.feature.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and token management endpoints")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        TokenResponse response = authService.authenticate(
                request,
                httpRequest.getHeader("User-Agent"),
                extractClientIp(httpRequest));
        setRefreshTokenCookie(httpRequest, httpResponse, response.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(authService.register(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        TokenResponse response = authService.refreshToken(
                new RefreshTokenRequest(extractRefreshToken(request, httpRequest)));
        setRefreshTokenCookie(httpRequest, httpResponse, response.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String refreshToken = extractRefreshToken(request, httpRequest);
        authService.logout(new RefreshTokenRequest(refreshToken));
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(authService.getMe(jwt.getSubject())));
    }

    private String extractRefreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String refreshToken = extractRefreshTokenFromCookie(httpRequest);
        if (refreshToken != null) {
            return refreshToken;
        }

        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }

        throw new UnauthorizedException("Refresh token is required");
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void setRefreshTokenCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String refreshToken) {
        response.setHeader("Set-Cookie",
                REFRESH_TOKEN_COOKIE_NAME + "=" + refreshToken
                        + "; HttpOnly; SameSite=Lax; Path=" + COOKIE_PATH
                        + secureCookieAttribute(request)
                        + "; Max-Age=" + jwtProperties.refreshTokenExpiration());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.setHeader("Set-Cookie",
                REFRESH_TOKEN_COOKIE_NAME + "=; Max-Age=0; Path=" + COOKIE_PATH
                        + "; HttpOnly; SameSite=Lax");
    }

    private String secureCookieAttribute(HttpServletRequest request) {
        if (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            return "; Secure";
        }
        return "";
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
