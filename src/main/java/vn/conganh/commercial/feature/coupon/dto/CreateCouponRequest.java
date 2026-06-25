package vn.conganh.commercial.feature.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

public record CreateCouponRequest(

        @NotBlank(message = "Code is required")
        @Size(max = 50, message = "Code must be at most 50 characters")
        String code,

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

        CouponStatus status
) {
}
