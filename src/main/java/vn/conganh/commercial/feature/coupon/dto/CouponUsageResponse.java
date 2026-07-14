package vn.conganh.commercial.feature.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.coupon.CouponUsage;

public record CouponUsageResponse(
        Long id,
        CouponResponse coupon,
        Long orderId,
        String orderCode,
        BigDecimal discountAmount,
        Instant usedAt
) {

    public static CouponUsageResponse fromEntity(CouponUsage usage) {
        return new CouponUsageResponse(
                usage.getId(),
                CouponResponse.fromEntity(usage.getCoupon()),
                usage.getOrder().getId(),
                usage.getOrder().getOrderCode(),
                usage.getDiscountAmount(),
                usage.getUsedAt());
    }
}
