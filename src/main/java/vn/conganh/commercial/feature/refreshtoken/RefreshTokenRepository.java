package vn.conganh.commercial.feature.refreshtoken;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    boolean existsByToken(String token);

    long deleteByUserIdAndRevokedTrue(Long userId);

    @Modifying
    @Transactional
    // Bulk delete rẻ hơn việc load từng token entity rồi xóa từng bản ghi.
    @Query("""
            delete from RefreshToken r
            where r.expiresAt < :now or r.revoked = true
            """)
    int deleteExpiredOrRevoked(Instant now);
}
