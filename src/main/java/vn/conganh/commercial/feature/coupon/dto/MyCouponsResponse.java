package vn.conganh.commercial.feature.coupon.dto;

import java.math.BigDecimal;
import java.util.List;
import vn.conganh.commercial.util.constant.CustomerTier;

public record MyCouponsResponse(
        CustomerTier membershipTier,
        String tierLabel,
        BigDecimal tierSpentAmount,
        CustomerTier nextTier,
        String nextTierLabel,
        BigDecimal amountToNextTier,
        int cycleDays,
        List<CouponResponse> availableCoupons,
        List<CouponUsageResponse> usageHistory
) {
}
