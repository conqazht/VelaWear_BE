package vn.conganh.commercial.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Locale;
import vn.conganh.commercial.exception.InvalidRequestException;

public final class FilterSpecifications {

    private FilterSpecifications() {
    }

    public static void addContains(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Expression<String> expression,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        predicates.add(cb.like(
                cb.lower(expression),
                "%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
    }

    public static <T> void addEquals(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Expression<T> expression,
            T value) {
        if (value != null) {
            predicates.add(cb.equal(expression, value));
        }
    }

    public static <T extends Comparable<? super T>> void addRange(
            List<Predicate> predicates,
            CriteriaBuilder cb,
            Expression<? extends T> expression,
            T from,
            T to,
            String fieldName) {
        validateRange(fieldName, from, to);

        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(expression, from));
        }

        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(expression, to));
        }
    }

    public static <T extends Comparable<? super T>> void validateRange(String fieldName, T from, T to) {
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new InvalidRequestException(fieldName + " range is invalid");
        }
    }

    public static void requireMatchingPathId(String fieldName, Long pathValue, Long queryValue) {
        if (queryValue != null && !queryValue.equals(pathValue)) {
            throw new InvalidRequestException(fieldName + " filter does not match path variable");
        }
    }
}
