package vn.conganh.commercial.feature.coupon.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import vn.conganh.commercial.util.constant.CouponStatus;
import vn.conganh.commercial.util.constant.CouponType;

public record CouponFilterRequest(
        String code,
        CouponType type,
        CouponStatus status,
        BigDecimal valueFrom,
        BigDecimal valueTo,
        BigDecimal minOrderAmountFrom,
        BigDecimal minOrderAmountTo,
        BigDecimal maxDiscountFrom,
        BigDecimal maxDiscountTo,
        Integer usageLimitFrom,
        Integer usageLimitTo,
        Integer usedCountFrom,
        Integer usedCountTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endTo
) {}
