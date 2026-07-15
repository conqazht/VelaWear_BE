package vn.conganh.commercial.feature.category;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.dto.CategoryTranslationRequest;
import vn.conganh.commercial.feature.category.dto.CategoryTranslationResponse;
import vn.conganh.commercial.feature.category.dto.CategoryTranslationsResponse;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryTranslationsRequest;

@Service
@RequiredArgsConstructor
public class CategoryTranslationServiceImpl implements CategoryTranslationService {

    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository translationRepository;
    private final CatalogLocaleResolver localeResolver;

    @Override
    @Transactional(readOnly = true)
    public CategoryTranslationsResponse getTranslations(Long categoryId) {
        findCategory(categoryId);
        return response(categoryId);
    }

    @Override
    @Transactional
    public CategoryTranslationsResponse updateTranslations(
            Long categoryId,
            UpdateCategoryTranslationsRequest request) {
        Category category = findCategoryWithLock(categoryId);
        Set<String> localeCodes = new HashSet<>();
        for (CategoryTranslationRequest item : request.translations()) {
            String localeCode = localeResolver.requireEnabledLocale(item.localeCode());
            if (!localeCodes.add(localeCode)) {
                throw new InvalidRequestException("Duplicate locale in translation batch: " + localeCode);
            }
            validateSlug(categoryId, localeCode, item.slug());
            CategoryTranslation translation = translationRepository
                    .findByCategoryIdAndLocaleCode(categoryId, localeCode)
                    .orElseGet(CategoryTranslation::new);
            apply(translation, categoryId, localeCode, item);
            translationRepository.save(translation);
            if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(localeCode)) {
                validateCoreSlug(categoryId, item.slug());
                category.setName(item.name().trim());
                category.setSlug(item.slug().trim());
            }
        }
        categoryRepository.save(category);
        translationRepository.flush();
        return response(categoryId);
    }

    @Override
    @Transactional
    public void deleteTranslation(Long categoryId, String localeCode) {
        findCategoryWithLock(categoryId);
        String resolvedLocale = localeResolver.requireEnabledLocale(localeCode);
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            throw new InvalidRequestException("The default locale translation cannot be deleted");
        }
        CategoryTranslation translation = translationRepository
                .findByCategoryIdAndLocaleCode(categoryId, resolvedLocale)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CategoryTranslation", "localeCode", resolvedLocale));
        translationRepository.delete(translation);
    }

    private void apply(
            CategoryTranslation translation,
            Long categoryId,
            String localeCode,
            CategoryTranslationRequest request) {
        translation.setCategoryId(categoryId);
        translation.setLocaleCode(localeCode);
        translation.setName(request.name().trim());
        translation.setSlug(request.slug().trim());
        translation.setDescription(request.description());
        translation.setSeoTitle(request.seoTitle());
        translation.setSeoDescription(request.seoDescription());
    }

    private void validateSlug(Long categoryId, String localeCode, String slug) {
        Optional<CategoryTranslation> owner = translationRepository
                .findByLocaleCodeAndSlug(localeCode, slug.trim());
        if (owner.isPresent() && !owner.get().getCategoryId().equals(categoryId)) {
            throw new InvalidRequestException("Category translation slug already exists for locale " + localeCode);
        }
    }

    private void validateCoreSlug(Long categoryId, String slug) {
        Optional<Category> owner = categoryRepository.findBySlug(slug.trim());
        if (owner.isPresent() && !owner.get().getId().equals(categoryId)) {
            throw new InvalidRequestException("Category slug already exists");
        }
    }

    private CategoryTranslationsResponse response(Long categoryId) {
        List<CategoryTranslationResponse> translations = translationRepository.findByCategoryId(categoryId).stream()
                .sorted((left, right) -> compareLocales(left.getLocaleCode(), right.getLocaleCode()))
                .map(CategoryTranslationResponse::fromEntity)
                .toList();
        return new CategoryTranslationsResponse(translations);
    }

    private int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        return left.compareTo(right);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
    }

    private Category findCategoryWithLock(Long categoryId) {
        return categoryRepository.findWithLockByIdAndDeletedAtIsNull(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
    }
}
