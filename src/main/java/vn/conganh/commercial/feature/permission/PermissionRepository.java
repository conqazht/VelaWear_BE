package vn.conganh.commercial.feature.permission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByApiPathAndMethod(String apiPath, String method);

    @Query("""
            select r.name as roleName, p as permission
            from RoleHasPermission rhp
            join rhp.role r
            join rhp.permission p
            """)
    List<RolePermissionView> findAllRolePermissions();
}
