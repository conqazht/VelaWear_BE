package vn.conganh.commercial.feature.coupon;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    boolean existsByOrderId(Long orderId);
}
