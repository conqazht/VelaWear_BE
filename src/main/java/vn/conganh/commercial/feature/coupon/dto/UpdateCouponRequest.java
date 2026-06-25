package vn.conganh.commercial.feature.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

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
        CouponStatus status
) {
}
