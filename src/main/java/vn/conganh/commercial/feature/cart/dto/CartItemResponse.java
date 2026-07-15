package vn.conganh.commercial.feature.cart.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record CartItemResponse(
        Long id,
        Long variantId,
        Long productId,
        String productSlug,
        String productName,
        String image,
        String sku,
        String color,
        String size,
        BigDecimal listPrice,
        BigDecimal price,
        VariantPricingResponse pricing,
        int quantity
) {}
