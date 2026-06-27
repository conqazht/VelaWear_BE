package vn.conganh.commercial.feature.wishlist.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record WishlistFilterRequest(
        Long userId,
        Long productId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {
    public WishlistFilterRequest withUserId(Long userId) {
        return new WishlistFilterRequest(userId, productId, createdFrom, createdTo);
    }

    public WishlistFilterRequest withProductId(Long productId) {
        return new WishlistFilterRequest(userId, productId, createdFrom, createdTo);
    }
}
