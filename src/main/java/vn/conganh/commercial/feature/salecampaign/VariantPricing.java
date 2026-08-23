package vn.conganh.commercial.feature.salecampaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public int salePriority() {
        return switch (priceSource) {
            case FLASH_SALE -> 2;
            case STANDARD_SALE -> 1;
            case BASE -> 0;
        };
    }

    public BigDecimal discountRate() {
        if (listPrice == null || listPrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return listPrice.subtract(effectivePrice)
                .max(BigDecimal.ZERO)
                .divide(listPrice, 6, RoundingMode.HALF_UP);
    }

    public VariantPricingResponse toResponse() {
        String campaignName = campaignItem == null ? null : campaignItem.getCampaign().getName();
        return toResponse(campaignName);
    }

    public VariantPricingResponse toResponse(String campaignName) {
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
                campaignName,
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                remainingQuota,
                campaignItem.getMaxPerCustomer(),
                customerRemaining,
                priceSource != PriceSource.FLASH_SALE,
                availableQuantity);
    }
}
