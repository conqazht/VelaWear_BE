package vn.conganh.commercial.feature.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.coupon.Coupon;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

public record CouponResponse(
        Long id,
        String code,
        CouponType type,
        BigDecimal value,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscount,
        Integer usageLimit,
        int usedCount,
        Instant startDate,
        Instant endDate,
        CouponStatus status
) {

    public static CouponResponse fromEntity(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscount(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getStatus());
    }
}
