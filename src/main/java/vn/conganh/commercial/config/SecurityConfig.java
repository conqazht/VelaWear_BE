package vn.conganh.commercial.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.security.PermissionAuthorizationManager;
import vn.conganh.commercial.security.TokenBlacklistService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
   private static final String[] WHITELIST = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health",
            "/actuator/info",
            "/uploads/**"
    };

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            PermissionAuthorizationManager permissionAuthorizationManager,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            TokenBlacklistService tokenBlacklistService,
            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Chạy sau bước xác thực JWT đã đưa dữ liệu vào SecurityContext, trước khi phân quyền.
                .addFilterBefore(new JwtBlacklistFilter(tokenBlacklistService, objectMapper), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITELIST).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**",
                                "/api/v1/categories",
                                "/api/v1/categories/**",
                                "/api/v1/brands",
                                "/api/v1/brands/**",
                                "/api/v1/product-variants",
                                "/api/v1/product-variants/**").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .anyRequest().access(permissionAuthorizationManager))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    private static class JwtBlacklistFilter extends OncePerRequestFilter {
        private final TokenBlacklistService tokenBlacklistService;
        private final ObjectMapper objectMapper;

        public JwtBlacklistFilter(TokenBlacklistService tokenBlacklistService, ObjectMapper objectMapper) {
            this.tokenBlacklistService = tokenBlacklistService;
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
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                    ApiResponse<Void> apiResponse = new ApiResponse<>(
                            HttpStatus.UNAUTHORIZED.value(),
                            null,
                            "Token is blacklisted",
                            LocalDateTime.now()
                    );

                    objectMapper.writeValue(response.getOutputStream(), apiResponse);
                    return;
                }

                Long userId = jwtAuth.getToken().getClaim("userId");
                Instant issuedAt = jwtAuth.getToken().getIssuedAt();
                if (userId != null && issuedAt != null) {
                    Long roleUpdateTimestamp = tokenBlacklistService.getRoleUpdateTimestamp(userId);
                    // Nếu role đổi sau lúc JWT được phát hành, bắt client refresh để nhận quyền mới.
                    if (roleUpdateTimestamp != null && issuedAt.toEpochMilli() < roleUpdateTimestamp) {
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                        ApiResponse<Void> apiResponse = new ApiResponse<>(
                                HttpStatus.UNAUTHORIZED.value(),
                                null,
                                "User roles have been updated. Please refresh token.",
                                LocalDateTime.now()
                        );

                        objectMapper.writeValue(response.getOutputStream(), apiResponse);
                        return;
                    }
                }
            }
            filterChain.doFilter(request, response);
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
                    authException.getMessage(),
                    LocalDateTime.now()
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
                    accessDeniedException.getMessage(),
                    LocalDateTime.now()
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
