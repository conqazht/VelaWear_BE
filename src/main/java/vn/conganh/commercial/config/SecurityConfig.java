package vn.conganh.commercial.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.auth.oauth2.CookieOAuth2AuthorizationRequestRepository;
import vn.conganh.commercial.feature.auth.oauth2.OAuth2AuthenticationFailureHandler;
import vn.conganh.commercial.feature.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import vn.conganh.commercial.security.PermissionAuthorizationManager;
import vn.conganh.commercial.security.TokenBlacklistService;
import vn.conganh.commercial.security.ratelimit.AuthRateLimitFilter;
import vn.conganh.commercial.feature.user.UserRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(OAuth2Properties.class)
public class SecurityConfig {
   private static final String[] WHITELIST = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/otp/request",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/forgot-password/reset",
            "/api/v1/auth/oauth2/exchange",
            "/oauth2/**",
            "/login/oauth2/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/payments/sepay/ipn",
            "/uploads/**"
    };

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            PermissionAuthorizationManager permissionAuthorizationManager,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            TokenBlacklistService tokenBlacklistService,
            UserRepository userRepository,
            AuthRateLimitFilter authRateLimitFilter,
            ObjectMapper objectMapper,
            CookieOAuth2AuthorizationRequestRepository cookieOAuth2AuthorizationRequestRepository,
            OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler,
            OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Limiter chạy sau CORS nhưng trước controller/body binding.
                .addFilterAfter(authRateLimitFilter, CorsFilter.class)
                // Chạy sau bước xác thực JWT đã đưa dữ liệu vào SecurityContext, trước khi phân quyền.
                .addFilterBefore(
                        new JwtSessionValidationFilter(tokenBlacklistService, userRepository, objectMapper),
                        AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .requestMatchers(EndpointRequest.to("metrics")).hasRole("ADMIN")
                        .requestMatchers(WHITELIST).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/*",
                                "/api/v1/products/slug/*",
                                "/api/v1/storefront/products",
                                "/api/v1/categories",
                                "/api/v1/categories/*",
                                "/api/v1/categories/slug/*",
                                "/api/v1/brands",
                                "/api/v1/brands/**",
                                "/api/v1/product-variants",
                                "/api/v1/product-variants/**",
                                "/api/v1/sales",
                                "/api/v1/sales/**",
                                "/api/v1/reviews/product/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/me",
                                "/api/v1/auth/me/email",
                                "/api/v1/auth/me/password",
                                "/api/v1/reviews/me",
                                "/api/v1/wishlists/me",
                                "/api/v1/wishlists/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reviews").authenticated()
                        .anyRequest().access(permissionAuthorizationManager))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(cookieOAuth2AuthorizationRequestRepository))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureHandler(oAuth2AuthenticationFailureHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> disableContainerRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static class JwtSessionValidationFilter extends OncePerRequestFilter {
        private final TokenBlacklistService tokenBlacklistService;
        private final UserRepository userRepository;
        private final ObjectMapper objectMapper;

        public JwtSessionValidationFilter(
                TokenBlacklistService tokenBlacklistService,
                UserRepository userRepository,
                ObjectMapper objectMapper) {
            this.tokenBlacklistService = tokenBlacklistService;
            this.userRepository = userRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String token = jwtAuth.getToken().getTokenValue();
                // Logout đưa Access Token vào blacklist; đoạn này chuyển trạng thái Redis đó thành phản hồi 401.
                if (tokenBlacklistService.isBlacklisted(token)) {
                    writeSessionRevoked(response);
                    return;
                }

                Object userIdClaim = jwtAuth.getToken().getClaim("userId");
                Object securityVersionClaim = jwtAuth.getToken().getClaim("securityVersion");
                if (!(userIdClaim instanceof Number userIdNumber)
                        || !(securityVersionClaim instanceof Number versionNumber)
                        || versionNumber.longValue() < 0) {
                    writeSessionRevoked(response);
                    return;
                }

                boolean current = userRepository.findSecurityVersionByIdAndDeletedAtIsNull(
                                userIdNumber.longValue())
                        .map(version -> version == versionNumber.longValue())
                        .orElse(false);
                if (!current) {
                    writeSessionRevoked(response);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }

        private void writeSessionRevoked(HttpServletResponse response) throws IOException {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(
                    HttpStatus.UNAUTHORIZED.value(),
                    null,
                    "Your session has been revoked. Please sign in again.",
                    LocalDateTime.now(),
                    "SESSION_REVOKED"));
        }
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            ApiResponse<Void> apiResponse = new ApiResponse<>(
                    HttpStatus.UNAUTHORIZED.value(),
                    null,
                    "Authentication required",
                    LocalDateTime.now(),
                    "AUTHENTICATION_REQUIRED"
            );

            objectMapper.writeValue(response.getOutputStream(), apiResponse);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            ApiResponse<Void> apiResponse = new ApiResponse<>(
                    HttpStatus.FORBIDDEN.value(),
                    null,
                    "Access denied",
                    LocalDateTime.now(),
                    "ACCESS_DENIED"
            );

            objectMapper.writeValue(response.getOutputStream(), apiResponse);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
