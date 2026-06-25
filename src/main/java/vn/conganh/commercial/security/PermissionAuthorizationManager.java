package vn.conganh.commercial.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import vn.conganh.commercial.feature.permission.Permission;
import vn.conganh.commercial.feature.permission.PermissionRepository;
import vn.conganh.commercial.feature.permission.RolePermissionView;

@Component
public class PermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PermissionRepository permissionRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private volatile Map<String, List<Permission>> rolePermissionsCache = Map.of();

    public PermissionAuthorizationManager(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
        refreshCache();
    }

    public void refreshCache() {
        List<RolePermissionView> rolePermissions = permissionRepository.findAllRolePermissions();

        Map<String, List<Permission>> cache = new HashMap<>();
        for (RolePermissionView rolePermission : rolePermissions) {
            String roleName = "ROLE_" + rolePermission.getRoleName();
            cache.computeIfAbsent(roleName, key -> new ArrayList<>())
                    .add(rolePermission.getPermission());
        }
        rolePermissionsCache = Collections.unmodifiableMap(cache);
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
            List<Permission> permissions = rolePermissionsCache.getOrDefault(role, List.of());
            for (Permission permission : permissions) {
                if (permission.getMethod().equalsIgnoreCase(httpMethod)
                        && pathMatcher.match(permission.getApiPath(), requestPath)) {
                    return new AuthorizationDecision(true);
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
