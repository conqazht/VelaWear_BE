package vn.conganh.commercial.feature.category;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class CategorySpecification {

    private CategorySpecification() {
    }

    public static PredicateSpecification<Category> build(CategoryFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("parentId"), filter.parentId());
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
