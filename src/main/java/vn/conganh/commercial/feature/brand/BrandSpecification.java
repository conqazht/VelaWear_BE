package vn.conganh.commercial.feature.brand;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.brand.dto.BrandFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class BrandSpecification {

    private BrandSpecification() {
    }

    public static PredicateSpecification<Brand> build(BrandFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("name"), filter.name());
                FilterSpecifications.addContains(predicates, cb, from.get("slug"), filter.slug());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
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
