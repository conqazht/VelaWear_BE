package vn.conganh.commercial.feature.review.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ReviewFilterRequest(
        Long userId,
        Long orderId,
        Long orderItemId,
        Short ratingFrom,
        Short ratingTo,
        String comment,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {
    public ReviewFilterRequest withUserId(Long userId) {
        return new ReviewFilterRequest(userId, orderId, orderItemId, ratingFrom, ratingTo, comment, createdFrom, createdTo);
    }

    public ReviewFilterRequest withOrderId(Long orderId) {
        return new ReviewFilterRequest(userId, orderId, orderItemId, ratingFrom, ratingTo, comment, createdFrom, createdTo);
    }

    public ReviewFilterRequest withOrderItemId(Long orderItemId) {
        return new ReviewFilterRequest(userId, orderId, orderItemId, ratingFrom, ratingTo, comment, createdFrom, createdTo);
    }
}
