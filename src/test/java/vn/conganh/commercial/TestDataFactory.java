package vn.conganh.commercial;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.conganh.commercial.security.PermissionAuthorizationManager;

@Component
public class TestDataFactory {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PermissionAuthorizationManager permissionAuthorizationManager;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    private static final String TEST_ROLE_NAME = "TEST_ROLE";

    @Transactional
    public void seedPermissions(String module, String apiPath, String... methods) {
        // 1. Seed Permissions
        List<Long> permissionIds = new ArrayList<>();
        for (String method : methods) {
            Long permissionId;
            List<Long> permIds = jdbcTemplate.queryForList(
                    "SELECT id FROM permissions WHERE api_path = ? AND method = ?",
                    Long.class, apiPath, method
            );
            if (permIds.isEmpty()) {
                permissionId = insertForId(
                        "INSERT INTO permissions (name, api_path, method, module) VALUES (?, ?, ?, ?) RETURNING id",
                        module + "_" + method + "_" + UUID.randomUUID().toString().substring(0, 8),
                        apiPath,
                        method,
                        module
                );
            } else {
                permissionId = permIds.get(0);
            }
            permissionIds.add(permissionId);
        }

        // 2. Find or create TEST_ROLE
        Long roleId;
        List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE name = ?",
                Long.class, TEST_ROLE_NAME
        );
        if (roleIds.isEmpty()) {
            roleId = insertForId(
                    "INSERT INTO roles (name, description) VALUES (?, ?) RETURNING id",
                    TEST_ROLE_NAME,
                    "Role for integration tests"
            );
        } else {
            roleId = roleIds.get(0);
        }

        // 3. Map permissions to role
        for (Long permissionId : permissionIds) {
            jdbcTemplate.update(
                    "INSERT INTO permission_role (permission_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    permissionId, roleId
            );
        }

        // Reload cache
        if (cacheManager.getCache("role_permissions") != null) {
            cacheManager.getCache("role_permissions").clear();
        }
    }

    @Transactional
    public String jwtWithPermission() {
        return jwtWithPermission("test@example.com");
    }

    @Transactional
    public String jwtWithPermission(String email) {
        // Find or create TEST_ROLE
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ?",
                Long.class, TEST_ROLE_NAME
        );

        // Create test user and link to role
        Long userId = insertForId(
                "INSERT INTO users (full_name, email, password, birth_date, gender) VALUES (?, ?, ?, ?, ?) RETURNING id",
                "Test User",
                email,
                "$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq",
                java.sql.Date.valueOf(java.time.LocalDate.of(1995, 1, 1)),
                "OTHER"
        );

        jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId, roleId
        );

        return generateToken(email, userId, List.of("ROLE_" + TEST_ROLE_NAME));
    }

    @Transactional
    public String jwtWithoutPermission() {
        String email = "noperm@example.com";
        Long userId = insertForId(
                "INSERT INTO users (full_name, email, password, birth_date, gender) VALUES (?, ?, ?, ?, ?) RETURNING id",
                "No Access User",
                email,
                "$2a$10$XPBc3MlN1.2ligKqIhCbHOG6rTvZd/k8JxKkZIcJQq2HFlpGMlwRq",
                java.sql.Date.valueOf(java.time.LocalDate.of(1995, 1, 1)),
                "OTHER"
        );

        return generateToken(email, userId, List.of());
    }

    @Transactional
    public void cleanup() {
        // Delete relationships first to avoid FK constraint errors
        jdbcTemplate.update("DELETE FROM user_role WHERE role_id IN (SELECT id FROM roles WHERE name = ?)", TEST_ROLE_NAME);
        jdbcTemplate.update("DELETE FROM permission_role WHERE role_id IN (SELECT id FROM roles WHERE name = ?)", TEST_ROLE_NAME);
        
        // Delete permissions starting with our dynamic module prefixes or related to test
        jdbcTemplate.update("DELETE FROM permissions WHERE name LIKE '%_GET_%' OR name LIKE '%_POST_%' OR name LIKE '%_PUT_%' OR name LIKE '%_DELETE_%'");
        
        // Delete TEST_ROLE
        jdbcTemplate.update("DELETE FROM roles WHERE name = ?", TEST_ROLE_NAME);

        // Delete users created by test factory
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'test%@example.com' OR email = 'noperm@example.com'");

        if (cacheManager.getCache("role_permissions") != null) {
            cacheManager.getCache("role_permissions").clear();
        }
    }

    private Long insertForId(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private String generateToken(String subject, Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("userId", userId)
                .claim("securityVersion", 0L)
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
