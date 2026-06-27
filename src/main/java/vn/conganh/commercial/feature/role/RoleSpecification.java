package vn.conganh.commercial.feature.role;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.role.dto.RoleFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class RoleSpecification {

    private RoleSpecification() {
    }

    public static PredicateSpecification<Role> build(RoleFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("name"), filter.name());
                FilterSpecifications.addContains(predicates, cb, from.get("description"), filter.description());
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
