package vn.conganh.commercial.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import vn.conganh.commercial.feature.permission.PermissionRepository;

@ExtendWith(MockitoExtension.class)
class PermissionAuthorizationManagerTest {

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionAuthorizationManager authorizationManager;

    @BeforeEach
    void setUp() {
        authorizationManager = new PermissionAuthorizationManager(permissionRepository);
    }

    @Test
    void authorize_matchesCachedPrimitivePermission() {
        when(permissionRepository.findPermissionKeysByRoleName("USER"))
                .thenReturn(List.of("GET /api/v1/coupons/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/coupons/me");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var decision = authorizationManager.authorize(
                () -> authentication,
                new RequestAuthorizationContext(request));

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isTrue();
    }

    @Test
    void authorize_ignoresMalformedCachedPermission() {
        when(permissionRepository.findPermissionKeysByRoleName("USER"))
                .thenReturn(List.of("invalid", "GET ", " POST/api/v1/orders"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user", "", List.of(new SimpleGrantedAuthority("USER")));

        var decision = authorizationManager.authorize(
                () -> authentication,
                new RequestAuthorizationContext(request));

        assertThat(decision).isNotNull();
        assertThat(decision.isGranted()).isFalse();
    }
}
