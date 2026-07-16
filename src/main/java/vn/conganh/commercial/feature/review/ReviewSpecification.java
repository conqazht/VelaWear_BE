package vn.conganh.commercial.feature.review;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.review.dto.ReviewFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class ReviewSpecification {

    private ReviewSpecification() {
    }

    public static Specification<Review> build(ReviewFilterRequest filter) {
        return (from, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("user").get("id"), filter.userId());
                FilterSpecifications.addEquals(
                        predicates,
                        cb,
                        from.get("orderItem").get("order").get("id"),
                        filter.orderId());
                FilterSpecifications.addEquals(predicates, cb, from.get("orderItem").get("id"), filter.orderItemId());
                
                if (filter.productId() != null) {
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ProductVariant> variantRoot = subquery.from(ProductVariant.class);
                    subquery.select(variantRoot.get("id"))
                            .where(cb.equal(variantRoot.get("product").get("id"), filter.productId()));
                    predicates.add(from.get("orderItem").get("variantId").in(subquery));
                }

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
