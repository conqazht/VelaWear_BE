package vn.conganh.commercial.feature.order;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.order.dto.OrderFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class OrderSpecification {

    private OrderSpecification() {
    }

    public static PredicateSpecification<Order> build(OrderFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("user").get("id"), filter.userId());
                FilterSpecifications.addContains(predicates, cb, from.get("orderCode"), filter.orderCode());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
                FilterSpecifications.addEquals(predicates, cb, from.get("paymentMethod"), filter.paymentMethod());
                FilterSpecifications.addEquals(predicates, cb, from.get("paymentStatus"), filter.paymentStatus());
                FilterSpecifications.addContains(predicates, cb, from.get("receiverName"), filter.receiverName());
                FilterSpecifications.addContains(predicates, cb, from.get("receiverPhone"), filter.receiverPhone());
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<BigDecimal>get("finalAmount"),
                        filter.finalAmountFrom(),
                        filter.finalAmountTo(),
                        "finalAmount");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("createdAt").as(LocalDate.class),
                        filter.createdFrom(),
                        filter.createdTo(),
                        "createdAt");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("updatedAt").as(LocalDate.class),
                        filter.updatedFrom(),
                        filter.updatedTo(),
                        "updatedAt");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
