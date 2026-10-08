package vn.conganh.commercial.feature.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;
import vn.conganh.commercial.util.constant.CustomerTier;

public record UpdateCouponRequest(

        @NotNull(message = "Type is required")
        CouponType type,

        @NotNull(message = "Value is required")
        @DecimalMin(value = "0.00", message = "Value must be >= 0")
        BigDecimal value,

        @DecimalMin(value = "0.00", message = "Min order amount must be >= 0")
        BigDecimal minOrderAmount,

        @DecimalMin(value = "0.00", message = "Max discount must be >= 0")
        BigDecimal maxDiscount,

        Integer usageLimit,

        @NotNull(message = "Start date is required")
        Instant startDate,

        @NotNull(message = "End date is required")
        Instant endDate,

        @NotNull(message = "Status is required")
        CouponStatus status,

        CustomerTier minTier
) {

    public UpdateCouponRequest(
            CouponType type,
            BigDecimal value,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscount,
            Integer usageLimit,
            Instant startDate,
            Instant endDate,
            CouponStatus status) {
        this(type, value, minOrderAmount, maxDiscount, usageLimit, startDate, endDate, status, CustomerTier.STANDARD);
    }
}
