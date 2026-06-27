package vn.conganh.commercial.feature.payment;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.payment.dto.PaymentFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class PaymentSpecification {

    private PaymentSpecification() {
    }

    public static PredicateSpecification<Payment> build(PaymentFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("order").get("id"), filter.orderId());
                FilterSpecifications.addEquals(predicates, cb, from.get("provider"), filter.provider());
                FilterSpecifications.addContains(predicates, cb, from.get("transactionCode"), filter.transactionCode());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
                FilterSpecifications.addRange(
                        predicates, cb, from.<BigDecimal>get("amount"), filter.amountFrom(), filter.amountTo(), "amount");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("paidAt").as(LocalDate.class),
                        filter.paidFrom(),
                        filter.paidTo(),
                        "paidAt");
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
