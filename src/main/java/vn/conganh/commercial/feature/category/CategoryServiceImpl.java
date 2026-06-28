package vn.conganh.commercial.feature.category;

import java.time.Instant;
import java.util.HashMap;
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
        Page<Category> categories = categoryRepository.findAll(Specification.where(CategorySpecification.build(filter)), pageable);
        Map<Long, CategoryTranslation> translations = loadTranslations(
                categories.getContent().stream().map(Category::getId).toList(),
                localeCode);
        return ResultPaginationDTO.fromPage(categories.map(category -> CategoryResponse.fromEntity(
                category,
                translations.get(category.getId()))));
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
        return CategoryResponse.fromEntity(category, resolveTranslation(category.getId(), localeCode).orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<CategoryTranslation> translation = categoryTranslationRepository.findByLocaleCodeAndSlug(resolvedLocale, slug);
        if (translation.isEmpty() && !CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            translation = categoryTranslationRepository.findByLocaleCodeAndSlug(CatalogLocaleResolver.DEFAULT_LOCALE, slug);
        }
        if (translation.isPresent()) {
            Category category = findCategory(translation.get().getCategoryId());
            return CategoryResponse.fromEntity(category, resolveTranslation(category.getId(), resolvedLocale).orElse(null));
        }

        return CategoryResponse.fromEntity(categoryRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug", slug)));
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
        return CategoryResponse.fromEntity(saved, translation);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findCategory(id);
        apply(category, request.parentId(), request.name(), category.getSlug(), request.sortOrder(), request.status());
        Category saved = categoryRepository.save(category);
        CategoryTranslation translation = saveDefaultTranslation(
                saved,
                request.name(),
                saved.getSlug(),
                null,
                request.name(),
                null);
        return CategoryResponse.fromEntity(saved, translation);
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

    private Optional<CategoryTranslation> resolveTranslation(Long categoryId, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<CategoryTranslation> translation =
                categoryTranslationRepository.findByCategoryIdAndLocaleCode(categoryId, resolvedLocale);
        if (translation.isPresent() || CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            return translation;
        }
        return categoryTranslationRepository.findByCategoryIdAndLocaleCode(categoryId, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    private Map<Long, CategoryTranslation> loadTranslations(List<Long> categoryIds, String localeCode) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, CategoryTranslation> translations = new HashMap<>(categoryTranslationRepository
                .findByCategoryIdInAndLocaleCode(categoryIds, CatalogLocaleResolver.DEFAULT_LOCALE)
                .stream()
                .collect(Collectors.toMap(CategoryTranslation::getCategoryId, Function.identity())));

        String resolvedLocale = normalizeLocale(localeCode);
        if (!CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, resolvedLocale)
                    .forEach(translation -> translations.put(translation.getCategoryId(), translation));
        }
        return translations;
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
        String normalized = localeCode.trim().replace('_', '-').toLowerCase();
        int regionSeparator = normalized.indexOf('-');
        return regionSeparator > 0 ? normalized.substring(0, regionSeparator) : normalized;
    }
}
