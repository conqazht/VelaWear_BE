package vn.conganh.commercial.feature.coupon.dto;

import java.util.List;

public record MyCouponsResponse(
        List<CouponResponse> availableCoupons,
        List<CouponUsageResponse> usageHistory
) {
}
