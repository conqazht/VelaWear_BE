package vn.conganh.commercial.feature.useraddress;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.useraddress.dto.UserAddressFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class UserAddressSpecification {

    private UserAddressSpecification() {
    }

    public static PredicateSpecification<UserAddress> build(UserAddressFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("user").get("id"), filter.userId());
                FilterSpecifications.addContains(predicates, cb, from.get("receiverName"), filter.receiverName());
                FilterSpecifications.addContains(predicates, cb, from.get("phone"), filter.phone());
                FilterSpecifications.addContains(predicates, cb, from.get("province"), filter.province());
                FilterSpecifications.addContains(predicates, cb, from.get("district"), filter.district());
                FilterSpecifications.addContains(predicates, cb, from.get("ward"), filter.ward());
                FilterSpecifications.addEquals(predicates, cb, from.get("isDefault"), filter.isDefault());
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
