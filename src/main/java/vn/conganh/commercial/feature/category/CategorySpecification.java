package vn.conganh.commercial.feature.category;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class CategorySpecification {

    private CategorySpecification() {
    }

    public static Specification<Category> build(CategoryFilterRequest filter, String localeCode) {
        return (from, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(from.get("deletedAt")));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("parentId"), filter.parentId());
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
                        Root<CategoryTranslation> subRootAny = subqueryAny.from(CategoryTranslation.class);
                        subqueryAny.select(subRootAny.get("categoryId"));
                        subqueryAny.where(cb.like(cb.lower(subRootAny.get("name")), pattern));

                        predicates.add(cb.or(
                            cb.like(cb.lower(from.get("name")), pattern),
                            from.get("id").in(subqueryAny)
                        ));
                    } else {
                        Predicate translationMatch;
                        Predicate coreMatch;

                        Subquery<Integer> existsViSub = query.subquery(Integer.class);
                        Root<CategoryTranslation> existsViRoot = existsViSub.from(CategoryTranslation.class);
                        existsViSub.select(cb.literal(1));
                        existsViSub.where(
                            cb.and(
                                cb.equal(existsViRoot.get("categoryId"), from.get("id")),
                                cb.equal(cb.lower(existsViRoot.get("localeCode")), "vi")
                            )
                        );
                        Predicate notExistsVi = cb.not(cb.exists(existsViSub));

                        if (localeCode.equalsIgnoreCase("vi")) {
                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<CategoryTranslation> subRootVi = subqueryVi.from(CategoryTranslation.class);
                            subqueryVi.select(subRootVi.get("categoryId"));
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
                            Root<CategoryTranslation> subRootReq = subqueryReq.from(CategoryTranslation.class);
                            subqueryReq.select(subRootReq.get("categoryId"));
                            subqueryReq.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootReq.get("localeCode")), localeCode.toLowerCase()),
                                    cb.like(cb.lower(subRootReq.get("name")), pattern)
                                )
                            );
                            Predicate matchesReq = from.get("id").in(subqueryReq);

                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<CategoryTranslation> subRootVi = subqueryVi.from(CategoryTranslation.class);
                            subqueryVi.select(subRootVi.get("categoryId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("name")), pattern)
                                )
                            );
                            Predicate matchesVi = from.get("id").in(subqueryVi);

                            Subquery<Integer> existsReqSub = query.subquery(Integer.class);
                            Root<CategoryTranslation> existsReqRoot = existsReqSub.from(CategoryTranslation.class);
                            existsReqSub.select(cb.literal(1));
                            existsReqSub.where(
                                cb.and(
                                    cb.equal(existsReqRoot.get("categoryId"), from.get("id")),
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
                        Root<CategoryTranslation> subRootAny = subqueryAny.from(CategoryTranslation.class);
                        subqueryAny.select(subRootAny.get("categoryId"));
                        subqueryAny.where(cb.like(cb.lower(subRootAny.get("slug")), pattern));

                        predicates.add(cb.or(
                            cb.like(cb.lower(from.get("slug")), pattern),
                            from.get("id").in(subqueryAny)
                        ));
                    } else {
                        Predicate translationMatch;
                        Predicate coreMatch;

                        Subquery<Integer> existsViSub = query.subquery(Integer.class);
                        Root<CategoryTranslation> existsViRoot = existsViSub.from(CategoryTranslation.class);
                        existsViSub.select(cb.literal(1));
                        existsViSub.where(
                            cb.and(
                                cb.equal(existsViRoot.get("categoryId"), from.get("id")),
                                cb.equal(cb.lower(existsViRoot.get("localeCode")), "vi")
                            )
                        );
                        Predicate notExistsVi = cb.not(cb.exists(existsViSub));

                        if (localeCode.equalsIgnoreCase("vi")) {
                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<CategoryTranslation> subRootVi = subqueryVi.from(CategoryTranslation.class);
                            subqueryVi.select(subRootVi.get("categoryId"));
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
                            Root<CategoryTranslation> subRootReq = subqueryReq.from(CategoryTranslation.class);
                            subqueryReq.select(subRootReq.get("categoryId"));
                            subqueryReq.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootReq.get("localeCode")), localeCode.toLowerCase()),
                                    cb.like(cb.lower(subRootReq.get("slug")), pattern)
                                )
                            );
                            Predicate matchesReq = from.get("id").in(subqueryReq);

                            Subquery<Long> subqueryVi = query.subquery(Long.class);
                            Root<CategoryTranslation> subRootVi = subqueryVi.from(CategoryTranslation.class);
                            subqueryVi.select(subRootVi.get("categoryId"));
                            subqueryVi.where(
                                cb.and(
                                    cb.equal(cb.lower(subRootVi.get("localeCode")), "vi"),
                                    cb.like(cb.lower(subRootVi.get("slug")), pattern)
                                )
                            );
                            Predicate matchesVi = from.get("id").in(subqueryVi);

                            Subquery<Integer> existsReqSub = query.subquery(Integer.class);
                            Root<CategoryTranslation> existsReqRoot = existsReqSub.from(CategoryTranslation.class);
                            existsReqSub.select(cb.literal(1));
                            existsReqSub.where(
                                cb.and(
                                    cb.equal(existsReqRoot.get("categoryId"), from.get("id")),
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
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
