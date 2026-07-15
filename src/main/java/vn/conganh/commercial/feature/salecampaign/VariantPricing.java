package vn.conganh.commercial.feature.salecampaign;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record VariantPricing(
        Long variantId,
        BigDecimal listPrice,
        BigDecimal effectivePrice,
        PriceSource priceSource,
        SaleCampaignItem campaignItem,
        Integer remainingQuota,
        Integer customerRemaining,
        int availableQuantity
) {
    public boolean isFlash() {
        return priceSource == PriceSource.FLASH_SALE;
    }

    public VariantPricingResponse toResponse() {
        if (campaignItem == null) {
            return new VariantPricingResponse(
                    listPrice, effectivePrice, priceSource,
                    null, null, null, null,
                    null, null, null, null, null,
                    true, availableQuantity);
        }
        SaleCampaign campaign = campaignItem.getCampaign();
        return new VariantPricingResponse(
                listPrice,
                effectivePrice,
                priceSource,
                campaign.getId(),
                campaignItem.getId(),
                campaign.getCode(),
                campaign.getName(),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                remainingQuota,
                campaignItem.getMaxPerCustomer(),
                customerRemaining,
                priceSource != PriceSource.FLASH_SALE,
                availableQuantity);
    }
}
