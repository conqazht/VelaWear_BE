package vn.conganh.commercial.feature.permission;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import vn.conganh.commercial.security.PermissionAccess;

public interface PermissionRepository extends JpaRepository<Permission, Long>, JpaSpecificationExecutor<Permission> {

    boolean existsByApiPathAndMethod(String apiPath, String method);

    @Query("""
            select r.name as roleName, p as permission
            from RoleHasPermission rhp
            join rhp.role r
            join rhp.permission p
            """)
    List<RolePermissionView> findAllRolePermissions();

    // Cache DTO nhẹ thay vì entity Permission để tránh serialize quan hệ/proxy của JPA.
    @Cacheable(value = "role_permissions", key = "#roleName")
    @Query("""
            select new vn.conganh.commercial.security.PermissionAccess(
                rhp.permission.apiPath,
                rhp.permission.method
            )
            from RoleHasPermission rhp
            where rhp.role.name = :roleName
            """)
    List<PermissionAccess> findPermissionsByRoleName(String roleName);
}
