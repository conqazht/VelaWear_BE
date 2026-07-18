package vn.conganh.commercial.feature.user;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from User u
            where u.id = :id
              and u.deletedAt is null
            """)
    Optional<User> findByIdAndDeletedAtIsNullWithLock(Long id);

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

    boolean existsByAvatar(String avatar);

    @Query("""
            select u.avatar
            from User u
            where u.avatar is not null
              and u.deletedAt is null
            """)
    List<String> findManagedAvatarReferences();

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
