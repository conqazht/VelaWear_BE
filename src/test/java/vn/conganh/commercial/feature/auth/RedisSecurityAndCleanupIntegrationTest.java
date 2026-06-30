package vn.conganh.commercial.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.feature.refreshtoken.RefreshToken;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenRepository;
import vn.conganh.commercial.feature.refreshtoken.RefreshTokenCleanupJob;
import vn.conganh.commercial.feature.role.RoleService;
import vn.conganh.commercial.feature.role.dto.UpdateRoleRequest;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.security.TokenBlacklistService;

@DisplayName("Redis Security and Cleanup Integration Tests")
class RedisSecurityAndCleanupIntegrationTest extends AuthenticatedIntegrationTest {

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenCleanupJob refreshTokenCleanupJob;

    @Autowired
    private vn.conganh.commercial.feature.role.RoleRepository roleRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        userRepository.findByEmailAndDeletedAtIsNull("user.update@velawear.local")
                .ifPresent(u -> {
                    jdbcTemplate.execute("delete from refresh_tokens where user_id = " + u.getId());
                    userRepository.delete(u);
                });
        userRepository.findByEmailAndDeletedAtIsNull("cleanup@velawear.local")
                .ifPresent(u -> {
                    jdbcTemplate.execute("delete from refresh_tokens where user_id = " + u.getId());
                    userRepository.delete(u);
                });

        roleRepository.findById(1L).ifPresent(role -> {
            role.setDescription("Administrator Role");
            roleRepository.save(role);
        });

        var cache = cacheManager.getCache("role_permissions");
        if (cache != null) {
            cache.evict("ADMIN");
        }
    }

    @Test
    @DisplayName("Verify that logout blacklists the access token and subsequent requests are rejected")
    void logout_blacklistsTokenAndRejectsSubsequentRequests() throws Exception {
        String token = userToken();

        // Verify request succeeds initially
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Perform logout with authorization header
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify subsequent request with same token is rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token is blacklisted"));
    }

    @Test
    @DisplayName("Verify that role update forces token refresh on subsequent requests")
    void roleUpdate_forcesTokenRefresh() throws Exception {
        User user = new User();
        user.setFullName("Update Test User");
        user.setEmail("user.update@velawear.local");
        user.setPassword("encodedPassword");
        user.setBirthDate(java.time.LocalDate.now());
        user.setGender(vn.conganh.commercial.util.constant.UserGender.OTHER);
        user = userRepository.save(user);

        String token = tokenWithRoles(user.getEmail(), user.getId(), List.of("ROLE_USER"));
        Long userId = user.getId();

        // Verify request succeeds initially
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Simulate role update timestamp setting in Redis
        tokenBlacklistService.setRoleUpdateTimestamp(userId);

        // Verify subsequent request is rejected due to force refresh requirement
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("User roles have been updated. Please refresh token."));
    }

    @Test
    @DisplayName("Verify that role-permission cache is hit and then evicted on updates")
    void rolePermissionCache_hitAndEvictOnUpdate() throws Exception {
        String token = adminToken();

        // Trigger cache population by calling a protected endpoint requiring authorization manager
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Verify that Redis cache is populated for ADMIN role
        var cache = cacheManager.getCache("role_permissions");
        assertThat(cache).isNotNull();
        assertThat(cache.get("ADMIN")).isNotNull();

        // Update role to evict the cache
        UpdateRoleRequest updateRequest = new UpdateRoleRequest("ADMIN", "Updated Admin Description");
        roleService.updateRole(1L, updateRequest);

        // Verify that the Redis cache for role_permissions has been evicted/cleared
        assertThat(cache.get("ADMIN")).isNull();
    }

    @Test
    @DisplayName("Verify that cleanup job successfully purges expired and revoked refresh tokens")
    void cleanupJob_purgesExpiredAndRevokedTokens() {
        User user = new User();
        user.setFullName("Cleanup Test User");
        user.setEmail("cleanup@velawear.local");
        user.setPassword("encodedPassword");
        user.setBirthDate(java.time.LocalDate.now());
        user.setGender(vn.conganh.commercial.util.constant.UserGender.OTHER);
        user = userRepository.save(user);

        // Create active, expired, and revoked refresh tokens
        RefreshToken activeToken = new RefreshToken();
        activeToken.setUser(user);
        activeToken.setToken("active_token_123");
        activeToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        activeToken.setRevoked(false);
        refreshTokenRepository.save(activeToken);

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setUser(user);
        expiredToken.setToken("expired_token_123");
        expiredToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expiredToken.setRevoked(false);
        refreshTokenRepository.save(expiredToken);

        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setUser(user);
        revokedToken.setToken("revoked_token_123");
        revokedToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        revokedToken.setRevoked(true);
        refreshTokenRepository.save(revokedToken);

        long countBefore = refreshTokenRepository.count();

        // Run the cleanup job
        refreshTokenCleanupJob.cleanupExpiredAndRevokedTokens();

        // Assert only active token remains
        long countAfter = refreshTokenRepository.count();
        assertThat(countAfter).isEqualTo(countBefore - 2);
        assertThat(refreshTokenRepository.findByToken("active_token_123")).isPresent();
        assertThat(refreshTokenRepository.findByToken("expired_token_123")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("revoked_token_123")).isEmpty();
    }
}
