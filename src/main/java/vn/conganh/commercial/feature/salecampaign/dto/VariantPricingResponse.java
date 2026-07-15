package vn.conganh.commercial.feature.salecampaign.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.salecampaign.PriceSource;

public record VariantPricingResponse(
        BigDecimal listPrice,
        BigDecimal effectivePrice,
        PriceSource priceSource,
        Long campaignId,
        Long campaignItemId,
        String campaignCode,
        String campaignName,
        Instant startsAt,
        Instant endsAt,
        Integer remainingQuota,
        Integer maxPerCustomer,
        Integer customerRemaining,
        boolean couponEligible,
        int availableQuantity
) {}
