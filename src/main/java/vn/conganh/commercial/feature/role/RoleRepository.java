package vn.conganh.commercial.feature.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.conganh.commercial.feature.permission.Permission;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @Query("""
            select rhp.permission
            from RoleHasPermission rhp
            where rhp.role.id = :roleId
            order by rhp.permission.module, rhp.permission.name
            """)
    List<Permission> findPermissionsByRoleId(Long roleId);
}

