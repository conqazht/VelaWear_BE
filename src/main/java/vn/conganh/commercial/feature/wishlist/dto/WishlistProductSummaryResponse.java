package vn.conganh.commercial.feature.wishlist.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record WishlistProductSummaryResponse(
        Long id,
        String slug,
        String name,
        String description,
        Long categoryId,
        String originalSlug,
        String shortDescription,
        String status,
        String image,
        String thumbnail,
        String categoryName,
        String categorySlug,
        BigDecimal price,
        VariantPricingResponse pricing
) {}
