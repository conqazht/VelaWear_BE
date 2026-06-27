package vn.conganh.commercial.feature.coupon;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.coupon.dto.CouponFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class CouponSpecification {

    private CouponSpecification() {
    }

    public static PredicateSpecification<Coupon> build(CouponFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("code"), filter.code());
                FilterSpecifications.addEquals(predicates, cb, from.get("type"), filter.type());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
                FilterSpecifications.addRange(
                        predicates, cb, from.<BigDecimal>get("value"), filter.valueFrom(), filter.valueTo(), "value");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<BigDecimal>get("minOrderAmount"),
                        filter.minOrderAmountFrom(),
                        filter.minOrderAmountTo(),
                        "minOrderAmount");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<BigDecimal>get("maxDiscount"),
                        filter.maxDiscountFrom(),
                        filter.maxDiscountTo(),
                        "maxDiscount");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<Integer>get("usageLimit"),
                        filter.usageLimitFrom(),
                        filter.usageLimitTo(),
                        "usageLimit");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<Integer>get("usedCount"),
                        filter.usedCountFrom(),
                        filter.usedCountTo(),
                        "usedCount");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("startDate").as(LocalDate.class),
                        filter.startFrom(),
                        filter.startTo(),
                        "startDate");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("endDate").as(LocalDate.class),
                        filter.endFrom(),
                        filter.endTo(),
                        "endDate");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
