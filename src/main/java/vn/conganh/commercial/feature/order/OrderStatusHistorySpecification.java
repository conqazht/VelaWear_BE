package vn.conganh.commercial.feature.order;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.order.dto.OrderStatusHistoryFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class OrderStatusHistorySpecification {

    private OrderStatusHistorySpecification() {
    }

    public static PredicateSpecification<OrderStatusHistory> build(
            Long orderId,
            OrderStatusHistoryFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(from.get("order").get("id"), orderId));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("fromStatus"), filter.fromStatus());
                FilterSpecifications.addEquals(predicates, cb, from.get("toStatus"), filter.toStatus());
                FilterSpecifications.addEquals(predicates, cb, from.get("changedBy"), filter.changedBy());
                FilterSpecifications.addContains(predicates, cb, from.get("reason"), filter.reason());
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("createdAt").as(LocalDate.class),
                        filter.createdFrom(),
                        filter.createdTo(),
                        "createdAt");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
