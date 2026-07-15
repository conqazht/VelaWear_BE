package vn.conganh.commercial.feature.salecampaign;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import vn.conganh.commercial.feature.productvariant.ProductVariant;

public interface VariantPricingService {
    Map<Long, VariantPricing> resolve(Collection<ProductVariant> variants, Instant now, Long userId);

    default Map<Long, VariantPricing> resolve(Collection<ProductVariant> variants) {
        return resolve(variants, Instant.now(), null);
    }
}
