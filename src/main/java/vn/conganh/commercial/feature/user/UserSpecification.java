package vn.conganh.commercial.feature.user;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.user.dto.UserFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class UserSpecification {

    private UserSpecification() {
    }

    public static PredicateSpecification<User> build(UserFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addContains(predicates, cb, from.get("fullName"), filter.fullName());
                FilterSpecifications.addContains(predicates, cb, from.get("email"), filter.email());
                FilterSpecifications.addEquals(predicates, cb, from.get("gender"), filter.gender());
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<LocalDate>get("birthDate"),
                        filter.birthDateFrom(),
                        filter.birthDateTo(),
                        "birthDate");
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
