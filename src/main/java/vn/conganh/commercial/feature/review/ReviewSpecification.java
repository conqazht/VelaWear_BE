package vn.conganh.commercial.feature.review;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class ReviewSpecification {

    private ReviewSpecification() {
    }

    public static PredicateSpecification<Review> build(ReviewFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("user").get("id"), filter.userId());
                FilterSpecifications.addEquals(predicates, cb, from.get("orderItem").get("order").get("id"), filter.orderId());
                FilterSpecifications.addEquals(predicates, cb, from.get("orderItem").get("id"), filter.orderItemId());
                FilterSpecifications.addRange(
                        predicates, cb, from.<Short>get("rating"), filter.ratingFrom(), filter.ratingTo(), "rating");
                FilterSpecifications.addContains(predicates, cb, from.get("comment"), filter.comment());
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
