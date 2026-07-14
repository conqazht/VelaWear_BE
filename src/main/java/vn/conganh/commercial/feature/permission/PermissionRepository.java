package vn.conganh.commercial.feature.permission;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    boolean existsByApiPathAndMethod(String apiPath, String method);

    @Query("""
            select r.name as roleName, p as permission
            from RoleHasPermission rhp
            join rhp.role r
            join rhp.permission p
            """)
    List<RolePermissionView> findAllRolePermissions();

    // Cache chuỗi primitive để nhiều app instance/DevTools classloader có thể dùng chung Redis an toàn.
    @Cacheable(value = "role_permissions", key = "#roleName")
    @Query("""
            select concat(concat(rhp.permission.method, ' '), rhp.permission.apiPath)
            from RoleHasPermission rhp
            where rhp.role.name = :roleName
            """)
    List<String> findPermissionKeysByRoleName(String roleName);
}
