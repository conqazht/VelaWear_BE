package vn.conganh.commercial.feature.wishlist.dto;

import jakarta.validation.constraints.NotNull;

public record CreateWishlistRequest(

        @NotNull(message = "User ID is required")
        Long userId,

        @NotNull(message = "Product ID is required")
        Long productId
) {
}
