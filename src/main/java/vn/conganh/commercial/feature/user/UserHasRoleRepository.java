package vn.conganh.commercial.feature.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserHasRoleRepository extends JpaRepository<UserHasRole, Long> {

    @Modifying
    @Transactional
    @Query("delete from UserHasRole uhr where uhr.user.id = :userId")
    void deleteByUserId(Long userId);
}
