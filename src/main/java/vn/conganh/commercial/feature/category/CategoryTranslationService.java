package vn.conganh.commercial.feature.category;

import vn.conganh.commercial.feature.category.dto.CategoryTranslationsResponse;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryTranslationsRequest;

public interface CategoryTranslationService {

    CategoryTranslationsResponse getTranslations(Long categoryId);

    CategoryTranslationsResponse updateTranslations(Long categoryId, UpdateCategoryTranslationsRequest request);

    void deleteTranslation(Long categoryId, String localeCode);
}
