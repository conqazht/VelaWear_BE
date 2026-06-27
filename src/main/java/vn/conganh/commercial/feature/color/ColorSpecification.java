package vn.conganh.commercial.feature.color;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.color.dto.ColorFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class ColorSpecification {

    private ColorSpecification() {
    }

    public static PredicateSpecification<Color> build(ColorFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("name"), filter.name());
                FilterSpecifications.addContains(predicates, cb, from.get("hexCode"), filter.hexCode());
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
