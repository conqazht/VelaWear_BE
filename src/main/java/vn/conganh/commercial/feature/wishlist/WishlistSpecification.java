package vn.conganh.commercial.feature.wishlist;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.wishlist.dto.WishlistFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class WishlistSpecification {

    private WishlistSpecification() {
    }

    public static PredicateSpecification<Wishlist> build(WishlistFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("user").get("id"), filter.userId());
                FilterSpecifications.addEquals(predicates, cb, from.get("product").get("id"), filter.productId());
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
