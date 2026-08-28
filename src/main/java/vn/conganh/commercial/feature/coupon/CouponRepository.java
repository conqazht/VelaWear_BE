package vn.conganh.commercial.feature.coupon;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.conganh.commercial.util.constant.CouponStatus;

public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    boolean existsByCode(String code);

    Optional<Coupon> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE c.id = :id")
    Optional<Coupon> findWithLockById(@Param("id") Long id);

    List<Coupon> findAllByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByEndDateAsc(
            CouponStatus status,
            Instant startDate,
            Instant endDate);

    @Modifying
    @Query("""
            UPDATE Coupon c
            SET c.usedCount = c.usedCount + 1
            WHERE c.id = :couponId
              AND c.status = vn.conganh.commercial.util.constant.CouponStatus.ACTIVE
              AND c.startDate <= :now
              AND c.endDate >= :now
              AND (c.usageLimit IS NULL OR c.usedCount < c.usageLimit)
            """)
    int consumeUsage(@Param("couponId") Long couponId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Coupon c
            SET c.usedCount = c.usedCount - 1
            WHERE c.id = :couponId
              AND c.usedCount > 0
            """)
    int releaseUsage(@Param("couponId") Long couponId);
}
