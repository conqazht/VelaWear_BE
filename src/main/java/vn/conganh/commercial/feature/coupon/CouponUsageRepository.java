package vn.conganh.commercial.feature.coupon;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<CouponUsage> findByOrderId(Long orderId);

    @Query("""
            select usage
            from CouponUsage usage
            join fetch usage.coupon
            join fetch usage.order
            where usage.user.id = :userId
            order by usage.usedAt desc
            """)
    List<CouponUsage> findAllDetailedByUserId(@Param("userId") Long userId);
}
