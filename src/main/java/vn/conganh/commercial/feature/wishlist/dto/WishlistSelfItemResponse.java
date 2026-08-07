package vn.conganh.commercial.feature.wishlist.dto;

import java.time.Instant;

public record WishlistSelfItemResponse(
        Long id,
        Long userId,
        Long productId,
        Instant createdAt,
        WishlistProductSummaryResponse product
) {}
