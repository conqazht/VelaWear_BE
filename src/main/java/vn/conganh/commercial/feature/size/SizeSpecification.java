package vn.conganh.commercial.feature.size;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.size.dto.SizeFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class SizeSpecification {

    private SizeSpecification() {
    }

    public static PredicateSpecification<Size> build(SizeFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("name"), filter.name());
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
