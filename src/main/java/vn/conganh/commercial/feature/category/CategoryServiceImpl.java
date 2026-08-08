package vn.conganh.commercial.feature.category;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.dto.CategoryFilterRequest;
import vn.conganh.commercial.feature.category.dto.CategoryResponse;
import vn.conganh.commercial.feature.category.dto.CreateCategoryRequest;
import vn.conganh.commercial.feature.category.dto.UpdateCategoryRequest;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCategories(CategoryFilterRequest filter, Pageable pageable) {
        return getAllCategories(filter, pageable, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllCategories(CategoryFilterRequest filter, Pageable pageable, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Page<Category> categories = categoryRepository.findAll(
                Specification.where(CategorySpecification.build(filter, resolvedLocale)),
                pageable);
        Map<Long, Map<String, CategoryTranslation>> translations = loadTranslationRows(
                categories.getContent().stream().map(Category::getId).toList());
        return ResultPaginationDTO.fromPage(categories.map(category -> CategoryResponse.fromEntity(
                category,
                mergeTranslation(
                        category,
                        resolvedLocale,
                        translations.getOrDefault(category.getId(), Map.of())),
                translationLocales(translations.getOrDefault(category.getId(), Map.of())))));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return getCategoryById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id, String localeCode) {
        Category category = findCategory(id);
        String resolvedLocale = normalizeLocale(localeCode);
        Map<String, CategoryTranslation> translations = loadTranslationRows(List.of(category.getId()))
                .getOrDefault(category.getId(), Map.of());
        return CategoryResponse.fromEntity(
                category,
                mergeTranslation(category, resolvedLocale, translations),
                translationLocales(translations));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<CategoryTranslation> translation = categoryTranslationRepository.findByLocaleCodeAndSlug(resolvedLocale, slug);
        if (translation.isEmpty() && !CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            translation = categoryTranslationRepository.findByLocaleCodeAndSlug(CatalogLocaleResolver.DEFAULT_LOCALE, slug);
        }
        if (translation.isEmpty()) {
            // Category URLs stay unchanged while the UI locale changes. Resolve the owner
            // from any localized slug before rendering it in the newly requested locale.
            translation = categoryTranslationRepository.findFirstBySlug(slug);
        }
        if (translation.isPresent()) {
            Category category = findCategory(translation.get().getCategoryId());
            return getCategoryById(category.getId(), resolvedLocale);
        }

        Category category = categoryRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug));
        return getCategoryById(category.getId(), resolvedLocale);
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())
                || categoryTranslationRepository.existsByLocaleCodeAndSlug(
                CatalogLocaleResolver.DEFAULT_LOCALE,
                request.slug())) {
            throw new InvalidRequestException("Category slug already exists");
        }
        Category category = new Category();
        apply(category, request.parentId(), request.name(), request.slug(), request.sortOrder(), request.status());
        Category saved = categoryRepository.save(category);
        CategoryTranslation translation = saveDefaultTranslation(
                saved,
                request.name(),
                request.slug(),
                null,
                request.name(),
                null);
        return CategoryResponse.fromEntity(
                saved,
                translation,
                storedTranslationLocales(saved.getId()));
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findCategoryWithLock(id);
        apply(category, request.parentId(), request.name(), category.getSlug(), request.sortOrder(), request.status());
        Category saved = categoryRepository.save(category);
        Optional<CategoryTranslation> existingTranslation = categoryTranslationRepository
                .findByCategoryIdAndLocaleCode(saved.getId(), CatalogLocaleResolver.DEFAULT_LOCALE);
        CategoryTranslation translation = existingTranslation.orElseGet(CategoryTranslation::new);
        translation.setCategoryId(saved.getId());
        translation.setLocaleCode(CatalogLocaleResolver.DEFAULT_LOCALE);
        translation.setName(request.name());
        translation.setSlug(saved.getSlug());
        if (existingTranslation.isEmpty()) {
            translation.setSeoTitle(request.name());
        }
        translation = categoryTranslationRepository.save(translation);
        return CategoryResponse.fromEntity(
                saved,
                translation,
                storedTranslationLocales(saved.getId()));
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(Long id, UpdateStatusRequest request) {
        String status = request.status().trim();
        if (!java.util.Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new InvalidRequestException("Category status is invalid: " + request.status());
        }
        Category category = findCategoryWithLock(id);
        category.setStatus(status);
        categoryRepository.save(category);
        return getCategoryById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        category.setDeletedAt(Instant.now());
        categoryRepository.save(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private void apply(Category category, Long parentId, String name, String slug, int sortOrder, String status) {
        category.setParentId(parentId);
        category.setName(name);
        category.setSlug(slug);
        category.setSortOrder(sortOrder);
        category.setStatus(status);
    }

    private Map<Long, Map<String, CategoryTranslation>> loadTranslationRows(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryTranslationRepository.findByCategoryIdIn(categoryIds).stream()
                .collect(Collectors.groupingBy(
                        CategoryTranslation::getCategoryId,
                        Collectors.toMap(CategoryTranslation::getLocaleCode, Function.identity())));
    }

    private Category findCategoryWithLock(Long id) {
        return categoryRepository.findWithLockByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private CategoryTranslation mergeTranslation(
            Category category,
            String localeCode,
            Map<String, CategoryTranslation> translations) {
        CategoryTranslation requested = translations.get(localeCode);
        CategoryTranslation defaultTranslation = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        CategoryTranslation merged = new CategoryTranslation();
        merged.setCategoryId(category.getId());
        merged.setLocaleCode(localeCode);
        merged.setName(firstValue(
                requested == null ? null : requested.getName(),
                defaultTranslation == null ? null : defaultTranslation.getName(),
                category.getName()));
        merged.setSlug(firstValue(
                requested == null ? null : requested.getSlug(),
                defaultTranslation == null ? null : defaultTranslation.getSlug(),
                category.getSlug()));
        merged.setDescription(firstValue(
                requested == null ? null : requested.getDescription(),
                defaultTranslation == null ? null : defaultTranslation.getDescription()));
        merged.setSeoTitle(firstValue(
                requested == null ? null : requested.getSeoTitle(),
                defaultTranslation == null ? null : defaultTranslation.getSeoTitle(),
                category.getName()));
        merged.setSeoDescription(firstValue(
                requested == null ? null : requested.getSeoDescription(),
                defaultTranslation == null ? null : defaultTranslation.getSeoDescription()));
        return merged;
    }

    private List<String> translationLocales(Map<String, ?> translations) {
        return translations.keySet().stream().sorted(this::compareLocales).toList();
    }

    private List<String> storedTranslationLocales(Long categoryId) {
        return categoryTranslationRepository.findByCategoryId(categoryId).stream()
                .map(CategoryTranslation::getLocaleCode)
                .distinct()
                .sorted(this::compareLocales)
                .toList();
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

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private CategoryTranslation saveDefaultTranslation(
            Category category,
            String name,
            String slug,
            String description,
            String seoTitle,
            String seoDescription) {
        CategoryTranslation translation = categoryTranslationRepository
                .findByCategoryIdAndLocaleCode(category.getId(), CatalogLocaleResolver.DEFAULT_LOCALE)
                .orElseGet(CategoryTranslation::new);
        translation.setCategoryId(category.getId());
        translation.setLocaleCode(CatalogLocaleResolver.DEFAULT_LOCALE);
        translation.setName(name);
        translation.setSlug(slug);
        translation.setDescription(description);
        translation.setSeoTitle(seoTitle);
        translation.setSeoDescription(seoDescription);
        return categoryTranslationRepository.save(translation);
    }

    private String normalizeLocale(String localeCode) {
        if (localeCode == null || localeCode.isBlank()) {
            return CatalogLocaleResolver.DEFAULT_LOCALE;
        }
        return localeCode.trim().replace('_', '-').toLowerCase();
    }
}
