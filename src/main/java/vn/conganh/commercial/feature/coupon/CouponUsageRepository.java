package vn.conganh.commercial.feature.coupon;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    boolean existsByOrderId(Long orderId);

    Optional<CouponUsage> findByOrderId(Long orderId);
}
