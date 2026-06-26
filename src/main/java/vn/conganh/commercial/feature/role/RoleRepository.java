package vn.conganh.commercial.feature.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @Query("""
            select r
            from UserHasRole uhr
            join uhr.role r
            where uhr.user.id = :userId
            """)
    List<Role> findAllByUserId(Long userId);
}
