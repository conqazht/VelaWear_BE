package vn.conganh.commercial.feature.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    List<User> findAllByDeletedAtIsNull();

    boolean existsByEmail(String email);
}
