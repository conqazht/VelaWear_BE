package vn.conganh.commercial.security;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import vn.conganh.commercial.feature.permission.PermissionRepository;

@Component
public class PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionRepository permissionRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PermissionAuthorizationManager(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<? extends Authentication> authenticationSupplier,
            RequestAuthorizationContext context) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        String requestPath = context.getRequest().getRequestURI();
        String httpMethod = context.getRequest().getMethod();

        for (String role : getUserRoles(authentication)) {
            String cleanRoleName = role.replace("ROLE_", "");
            // Method repository có @Cacheable, nên Redis được kiểm tra trước khi fallback về PostgreSQL.
            List<String> permissions = permissionRepository.findPermissionKeysByRoleName(cleanRoleName);
            if (permissions != null) {
                for (String permission : permissions) {
                    int separator = permission.indexOf(' ');
                    if (separator <= 0 || separator == permission.length() - 1) {
                        continue;
                    }
                    String method = permission.substring(0, separator);
                    String apiPath = permission.substring(separator + 1);
                    // Một permission chỉ cho phép truy cập khi khớp cả HTTP method và API path kiểu ant.
                    if (method.equalsIgnoreCase(httpMethod)
                            && pathMatcher.match(apiPath, requestPath)) {
                        return new AuthorizationDecision(true);
                    }
                }
            }
        }

        return new AuthorizationDecision(false);
    }

    private List<String> getUserRoles(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            List<String> roles = jwtToken.getToken().getClaimAsStringList("roles");
            return roles == null ? List.of() : roles;
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .toList();
    }
}
