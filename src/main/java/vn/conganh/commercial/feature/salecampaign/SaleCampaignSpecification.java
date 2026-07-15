package vn.conganh.commercial.feature.salecampaign;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import vn.conganh.commercial.feature.salecampaign.dto.SaleCampaignFilterRequest;

public final class SaleCampaignSpecification {
    private SaleCampaignSpecification() {}

    public static Specification<SaleCampaign> build(SaleCampaignFilterRequest filter, Instant now) {
        return (root, query, cb) -> {
            if (filter == null) {
                return cb.conjunction();
            }
            var predicates = new ArrayList<Predicate>();
            if (filter.search() != null && !filter.search().isBlank()) {
                String term = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), term),
                        cb.like(cb.lower(root.get("code")), term)));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.phase() != null) {
                switch (filter.phase()) {
                    case UPCOMING -> {
                        predicates.add(cb.notEqual(root.get("status"), SaleCampaignStatus.CANCELLED));
                        predicates.add(cb.greaterThan(root.get("startsAt"), now));
                    }
                    case LIVE -> {
                        predicates.add(cb.notEqual(root.get("status"), SaleCampaignStatus.CANCELLED));
                        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), now));
                        predicates.add(cb.greaterThan(root.get("endsAt"), now));
                    }
                    case ENDED -> predicates.add(cb.or(
                            cb.equal(root.get("status"), SaleCampaignStatus.CANCELLED),
                            cb.lessThanOrEqualTo(root.get("endsAt"), now)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
