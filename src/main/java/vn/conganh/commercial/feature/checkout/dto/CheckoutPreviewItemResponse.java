package vn.conganh.commercial.feature.checkout.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.salecampaign.PriceSource;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record CheckoutPreviewItemResponse(
        Long variantId,
        Long productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal listPrice,
        BigDecimal price,
        BigDecimal subtotal,
        PriceSource priceSource,
        Long saleCampaignItemId,
        String saleCampaignCode,
        String saleCampaignName,
        VariantPricingResponse pricing,
        boolean couponEligible
) {}
