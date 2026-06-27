package vn.conganh.commercial.feature.permission;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.permission.dto.PermissionFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class PermissionSpecification {

    private PermissionSpecification() {
    }

    public static PredicateSpecification<Permission> build(PermissionFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("name"), filter.name());
                FilterSpecifications.addContains(predicates, cb, from.get("apiPath"), filter.apiPath());
                FilterSpecifications.addContains(predicates, cb, from.get("method"), filter.method());
                FilterSpecifications.addContains(predicates, cb, from.get("module"), filter.module());
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
