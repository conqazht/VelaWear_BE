package vn.conganh.commercial.feature.storefrontcatalog;

import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontCatalogResponse;
import vn.conganh.commercial.feature.storefrontcatalog.dto.StorefrontProductQuery;

public interface StorefrontCatalogService {

    StorefrontCatalogResponse getProducts(StorefrontProductQuery query, String localeCode);
}
