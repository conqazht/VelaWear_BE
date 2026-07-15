package vn.conganh.commercial.feature.product;

import vn.conganh.commercial.feature.product.dto.ProductTranslationsResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductTranslationsRequest;

public interface ProductTranslationService {

    ProductTranslationsResponse getTranslations(Long productId);

    ProductTranslationsResponse updateTranslations(Long productId, UpdateProductTranslationsRequest request);

    void deleteTranslation(Long productId, String localeCode);
}
