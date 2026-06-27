package vn.conganh.commercial.feature.product;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static PredicateSpecification<Product> build(ProductFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("categoryId"), filter.categoryId());
                FilterSpecifications.addEquals(predicates, cb, from.get("brandId"), filter.brandId());
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
