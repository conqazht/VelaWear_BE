package vn.conganh.commercial.feature.storefrontcatalog;

import java.math.BigDecimal;
import java.util.List;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Item;
import vn.conganh.commercial.feature.storefrontcatalog.StorefrontCatalogSnapshot.Offer;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;

record StorefrontCatalogEvaluation(
        List<Candidate> candidates,
        StorefrontCatalogResponse.Facets facets
) {

    record Candidate(
            Item item,
            List<Offer> matchingOffers,
            Offer representative,
            int salePriority,
            BigDecimal discountRate
    ) {}
}
