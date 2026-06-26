package vn.conganh.commercial.feature.refreshtoken;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    boolean existsByToken(String token);

    long deleteByUserIdAndRevokedTrue(Long userId);
}
