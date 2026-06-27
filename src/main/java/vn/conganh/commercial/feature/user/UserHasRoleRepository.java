package vn.conganh.commercial.feature.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.conganh.commercial.feature.permission.Permission;
import vn.conganh.commercial.feature.role.Role;

public interface UserHasRoleRepository extends JpaRepository<UserHasRole, Long> {

    @Query("""
            select uhr.role
            from UserHasRole uhr
            where uhr.user.id = :userId
            order by uhr.role.id
            """)
    List<Role> findRolesByUserId(Long userId);

    @Query("""
            select uhr
            from UserHasRole uhr
            join fetch uhr.role
            where uhr.user.id in :userIds
            order by uhr.user.id, uhr.role.id
            """)
    List<UserHasRole> findAllWithRoleByUserIdIn(List<Long> userIds);

    @Query("""
            select distinct rhp.permission
            from UserHasRole uhr
            join RoleHasPermission rhp on rhp.role.id = uhr.role.id
            where uhr.user.id = :userId
            order by rhp.permission.module, rhp.permission.name
            """)
    List<Permission> findEffectivePermissionsByUserId(Long userId);
}
