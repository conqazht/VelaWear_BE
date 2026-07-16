package vn.conganh.commercial.feature.review;

import java.util.Locale;
import org.springframework.data.domain.Sort;
import vn.conganh.commercial.exception.InvalidRequestException;

public enum PublicReviewSort {
    NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    OLDEST(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))),
    RATING_HIGH(Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    RATING_LOW(Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    private final Sort sort;

    PublicReviewSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }

    public static PublicReviewSort from(String value) {
        String normalized = value == null ? "newest" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "newest", "createdat,desc" -> NEWEST;
            case "oldest", "createdat,asc" -> OLDEST;
            case "rating-high", "rating,desc" -> RATING_HIGH;
            case "rating-low", "rating,asc" -> RATING_LOW;
            default -> throw new InvalidRequestException("Invalid public review sort: " + value);
        };
    }
}
