package vn.conganh.commercial.feature.product;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;
import vn.conganh.commercial.feature.productvariant.ProductVariant;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> build(ProductFilterRequest filter, String localeCode) {
        return (from, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("categoryId"), filter.categoryId());
                FilterSpecifications.addEquals(predicates, cb, from.get("brandId"), filter.brandId());
                FilterSpecifications.addEquals(predicates, cb, from.get("status"), filter.status());
                FilterSpecifications.addRange(
                        predicates,
                        cb,
                        from.get("createdAt").as(LocalDate.class),
                        filter.createdFrom(),
                        filter.createdTo(),
                        "createdAt");

                if (filter.name() != null && !filter.name().isBlank()) {
                    String pattern = "%" + filter.name().toLowerCase() + "%";

                    if (Boolean.TRUE.equals(filter.global())) {
                        Subquery<Long> subqueryAny = query.subquery(Long.class);
                        Root<ProductTranslation> subRootAny = subqueryAny.from(ProductTranslation.class);
                        subqueryAny.select(subRootAny.get("productId"));
                        subqueryAny.where(cb.like(cb.lower(subRootAny.get("name")), pattern));

                        predicates.add(cb.or(
                            cb.like(cb.lower(from.get("name")), pattern),
                            from.get("id").in(subqueryAny)
                        ));
                    } else {
                        Predicate translationMatch;
                        Predicate coreMatch;

                        Subquery<Integer> existsViSub = query.subquery(Integer.class);
                        Root<ProductTranslation> existsViRoot = existsViSub.from(ProductTranslation.class);
                        existsViSub.select(cb.literal(1));
                        existsViSub.where(
                            cb.and(
                                cb.equal(existsViRoot.get("productId"), from.get("id")),
                                cb.equal(cb.lower(existsViRoot.get("localeCode")), "vi")
                            )
                        );
                        Predicate notExistsVi = cb.not(cb.exists(existsViSub));

                        if (localeCode.equalsIgnoreCase("vi")) {
                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<ProductTranslation> subRootVi = subqueryVi.from(ProductTranslation.class);
                            subqueryVi.select(subRootVi.get("productId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("name")), pattern)
                                )
                            );
                            translationMatch = from.get("id").in(subqueryVi);
                            coreMatch = cb.and(
                                cb.like(cb.lower(from.get("name")), pattern),
                                notExistsVi
                            );
                        } else {
                            Subquery<Long> subqueryReq = query.subquery(Long.class);
                            Root<ProductTranslation> subRootReq = subqueryReq.from(ProductTranslation.class);
                            subqueryReq.select(subRootReq.get("productId"));
                            subqueryReq.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootReq.get("localeCode")), localeCode.toLowerCase()),
                                    cb.like(cb.lower(subRootReq.get("name")), pattern)
                                )
                            );
                            Predicate matchesReq = from.get("id").in(subqueryReq);

                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<ProductTranslation> subRootVi = subqueryVi.from(ProductTranslation.class);
                            subqueryVi.select(subRootVi.get("productId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("name")), pattern)
                                )
                            );
                            Predicate matchesVi = from.get("id").in(subqueryVi);

                            Subquery<Integer> existsReqSub = query.subquery(Integer.class);
                            Root<ProductTranslation> existsReqRoot = existsReqSub.from(ProductTranslation.class);
                            existsReqSub.select(cb.literal(1));
                            existsReqSub.where(
                                cb.and(
                                    cb.equal(existsReqRoot.get("productId"), from.get("id")),
                                    cb.equal(cb.lower(existsReqRoot.get("localeCode")), localeCode.toLowerCase())
                                )
                            );
                            Predicate notExistsReq = cb.not(cb.exists(existsReqSub));

                            translationMatch = cb.or(matchesReq, cb.and(matchesVi, notExistsReq));
                            coreMatch = cb.and(
                                cb.like(cb.lower(from.get("name")), pattern),
                                notExistsReq,
                                notExistsVi
                            );
                        }

                        predicates.add(cb.or(coreMatch, translationMatch));
                    }
                }

                if (filter.slug() != null && !filter.slug().isBlank()) {
                    String pattern = "%" + filter.slug().toLowerCase() + "%";

                    if (Boolean.TRUE.equals(filter.global())) {
                        Subquery<Long> subqueryAny = query.subquery(Long.class);
                        Root<ProductTranslation> subRootAny = subqueryAny.from(ProductTranslation.class);
                        subqueryAny.select(subRootAny.get("productId"));
                        subqueryAny.where(cb.like(cb.lower(subRootAny.get("slug")), pattern));

                        predicates.add(cb.or(
                            cb.like(cb.lower(from.get("slug")), pattern),
                            from.get("id").in(subqueryAny)
                        ));
                    } else {
                        Predicate translationMatch;
                        Predicate coreMatch;

                        Subquery<Integer> existsViSub = query.subquery(Integer.class);
                        Root<ProductTranslation> existsViRoot = existsViSub.from(ProductTranslation.class);
                        existsViSub.select(cb.literal(1));
                        existsViSub.where(
                            cb.and(
                                cb.equal(existsViRoot.get("productId"), from.get("id")),
                                cb.equal(cb.lower(existsViRoot.get("localeCode")), "vi")
                            )
                        );
                        Predicate notExistsVi = cb.not(cb.exists(existsViSub));

                        if (localeCode.equalsIgnoreCase("vi")) {
                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<ProductTranslation> subRootVi = subqueryVi.from(ProductTranslation.class);
                            subqueryVi.select(subRootVi.get("productId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("slug")), pattern)
                                )
                            );
                            translationMatch = from.get("id").in(subqueryVi);
                            coreMatch = cb.and(
                                cb.like(cb.lower(from.get("slug")), pattern),
                                notExistsVi
                            );
                        } else {
                            Subquery<Long> subqueryReq = query.subquery(Long.class);
                            Root<ProductTranslation> subRootReq = subqueryReq.from(ProductTranslation.class);
                            subqueryReq.select(subRootReq.get("productId"));
                            subqueryReq.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootReq.get("localeCode")), localeCode.toLowerCase()),
                                    cb.like(cb.lower(subRootReq.get("slug")), pattern)
                                )
                            );
                            Predicate matchesReq = from.get("id").in(subqueryReq);

                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<ProductTranslation> subRootVi = subqueryVi.from(ProductTranslation.class);
                            subqueryVi.select(subRootVi.get("productId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("slug")), pattern)
                                )
                            );
                            Predicate matchesVi = from.get("id").in(subqueryVi);

                            Subquery<Integer> existsReqSub = query.subquery(Integer.class);
                            Root<ProductTranslation> existsReqRoot = existsReqSub.from(ProductTranslation.class);
                            existsReqSub.select(cb.literal(1));
                            existsReqSub.where(
                                cb.and(
                                    cb.equal(existsReqRoot.get("productId"), from.get("id")),
                                    cb.equal(cb.lower(existsReqRoot.get("localeCode")), localeCode.toLowerCase())
                                )
                            );
                            Predicate notExistsReq = cb.not(cb.exists(existsReqSub));

                            translationMatch = cb.or(matchesReq, cb.and(matchesVi, notExistsReq));
                            coreMatch = cb.and(
                                cb.like(cb.lower(from.get("slug")), pattern),
                                notExistsReq,
                                notExistsVi
                            );
                        }

                        predicates.add(cb.or(coreMatch, translationMatch));
                    }
                }

                if (filter.colorId() != null || filter.sizeId() != null || filter.minPrice() != null || filter.maxPrice() != null) {
                    Subquery<Long> variantSub = query.subquery(Long.class);
                    Root<ProductVariant> variantRoot = variantSub.from(ProductVariant.class);
                    variantSub.select(variantRoot.get("product").get("id"));

                    List<Predicate> subPredicates = new ArrayList<>();
                    subPredicates.add(cb.isNull(variantRoot.get("deletedAt")));
                    subPredicates.add(cb.equal(variantRoot.get("status"), "ACTIVE"));

                    if (filter.colorId() != null) {
                        subPredicates.add(cb.equal(variantRoot.get("color").get("id"), filter.colorId()));
                    }
                    if (filter.sizeId() != null) {
                        subPredicates.add(cb.equal(variantRoot.get("size").get("id"), filter.sizeId()));
                    }

                    if (filter.minPrice() != null || filter.maxPrice() != null) {
                        jakarta.persistence.criteria.Expression<java.math.BigDecimal> activePrice =
                                cb.coalesce(variantRoot.get("salePrice"), variantRoot.get("price"));

                        if (filter.minPrice() != null) {
                            subPredicates.add(cb.greaterThanOrEqualTo(activePrice, filter.minPrice()));
                        }
                        if (filter.maxPrice() != null) {
                            subPredicates.add(cb.lessThanOrEqualTo(activePrice, filter.maxPrice()));
                        }
                    }

                    variantSub.where(subPredicates.toArray(new Predicate[0]));
                    predicates.add(from.get("id").in(variantSub));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
