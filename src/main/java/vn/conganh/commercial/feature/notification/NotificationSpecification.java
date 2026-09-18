package vn.conganh.commercial.feature.notification;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.PredicateSpecification;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.util.FilterSpecifications;

public final class NotificationSpecification {

    private NotificationSpecification() {}

    public static PredicateSpecification<Notification> build(Long userId, NotificationFilterRequest filter) {
        return (from, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(from.get("user").get("id"), userId));

            if (filter != null) {
                FilterSpecifications.addEquals(predicates, cb, from.get("isRead"), filter.isRead());
                FilterSpecifications.addEquals(predicates, cb, from.get("type"), filter.type());
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
