package vn.conganh.commercial.feature.storefrontcatalog;

import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogEvaluation.Candidate;

@Component
class StorefrontCatalogProductMapper {

    ProductResponse toResponse(Candidate candidate, StorefrontCatalogSnapshot snapshot) {
        VariantPricing pricing = candidate.representative().pricing();
        return ProductResponse.fromEntity(
                candidate.item().product(),
                candidate.item().translation(),
                snapshot.imagesByProduct().get(candidate.item().product().getId()),
                candidate.item().categoryName(),
                candidate.item().category().getSlug(),
                pricing.listPrice(),
                toPricingResponse(pricing, snapshot),
                candidate.item().translationLocales());
    }

    private VariantPricingResponse toPricingResponse(
            VariantPricing pricing,
            StorefrontCatalogSnapshot snapshot) {
        if (pricing.campaignItem() == null) {
            return pricing.toResponse();
        }
        Long campaignId = pricing.campaignItem().getCampaign().getId();
        return pricing.toResponse(snapshot.campaignNames().getOrDefault(
                campaignId,
                pricing.campaignItem().getCampaign().getName()));
    }
}
