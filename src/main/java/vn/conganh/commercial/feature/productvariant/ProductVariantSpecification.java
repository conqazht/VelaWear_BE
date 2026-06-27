package vn.conganh.commercial.feature.productvariant;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class ProductVariantSpecification {

    private ProductVariantSpecification() {
    }

    public static PredicateSpecification<ProductVariant> build(ProductVariantFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("product").get("id"), filter.productId());
                FilterSpecifications.addEquals(predicates, cb, from.get("color").get("id"), filter.colorId());
                FilterSpecifications.addEquals(predicates, cb, from.get("size").get("id"), filter.sizeId());
                FilterSpecifications.addContains(predicates, cb, from.get("sku"), filter.sku());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
                FilterSpecifications.addRange(
                        predicates, cb, from.<BigDecimal>get("price"), filter.priceFrom(), filter.priceTo(), "price");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<BigDecimal>get("salePrice"),
                        filter.salePriceFrom(),
                        filter.salePriceTo(),
                        "salePrice");
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.<Integer>get("stockQuantity"),
                        filter.stockFrom(),
                        filter.stockTo(),
                        "stockQuantity");
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
