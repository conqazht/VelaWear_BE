package vn.conganh.commercial.feature.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.permission.Permission;
import vn.conganh.commercial.feature.role.Role;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Transactional
    @Query("""
            update User u
            set u.securityVersion = u.securityVersion + 1
            where u.id = :userId
            """)
    int incrementSecurityVersion(Long userId);

    @Query("""
            select u.securityVersion
            from User u
            where u.id = :userId
              and u.deletedAt is null
            """)
    Optional<Long> findSecurityVersionByIdAndDeletedAtIsNull(Long userId);

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
