package vn.conganh.commercial.feature.wishlist.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.wishlist.Wishlist;

public record WishlistResponse(
        Long id,
        Long userId,
        Long productId,
        Instant createdAt
) {

    public static WishlistResponse fromEntity(Wishlist wishlist) {
        return new WishlistResponse(
                wishlist.getId(),
                wishlist.getUser().getId(),
                wishlist.getProduct().getId(),
                wishlist.getCreatedAt());
    }
}
