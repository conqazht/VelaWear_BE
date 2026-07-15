package vn.conganh.commercial.feature.product;

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
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.category.Category;
import vn.conganh.commercial.feature.category.CategoryRepository;
import vn.conganh.commercial.feature.category.CategoryTranslation;
import vn.conganh.commercial.feature.category.CategoryTranslationRepository;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VariantPricingService variantPricingService;
    private final SaleCampaignItemRepository saleCampaignItemRepository;
    private final SaleCampaignTranslationRepository saleCampaignTranslationRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllProducts(ProductFilterRequest filter, Pageable pageable) {
        return getAllProducts(filter, pageable, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllProducts(ProductFilterRequest filter, Pageable pageable, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Page<Product> products = productRepository.findAll(
                Specification.where(ProductSpecification.build(filter, resolvedLocale)),
                pageable);
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, Map<String, ProductTranslation>> translations = loadTranslationRows(productIds);
        List<ProductImage> allImages = productImageRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductImage>> imagesMap = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        List<Long> categoryIds = products.getContent().stream().map(Product::getCategoryId).distinct().toList();
        List<Category> allCategories = categoryRepository.findAllById(categoryIds);
        Map<Long, Category> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        
        Map<Long, Map<String, CategoryTranslation>> categoryTranslations = loadCategoryTranslationRows(categoryIds);

        Map<Long, VariantPricing> representativePrices = loadRepresentativePrices(productIds);
        Map<Long, String> campaignNames = loadCampaignNames(representativePrices.values(), resolvedLocale);

        return ResultPaginationDTO.fromPage(products.map(product -> {
            Category category = categoryMap.get(product.getCategoryId());
            CategoryTranslation categoryTranslation = category == null
                    ? null
                    : mergeCategoryTranslation(
                            category,
                            resolvedLocale,
                            categoryTranslations.getOrDefault(category.getId(), Map.of()));
            String catName = categoryTranslation == null ? null : categoryTranslation.getName();
            String catSlug = categoryTranslation == null ? null : categoryTranslation.getSlug();
            
            VariantPricing representativePrice = representativePrices.get(product.getId());

            return ProductResponse.fromEntity(
                    product,
                    mergeProductTranslation(
                            product,
                            resolvedLocale,
                            translations.getOrDefault(product.getId(), Map.of())),
                    imagesMap.get(product.getId()),
                    catName,
                    catSlug,
                    representativePrice == null ? null : representativePrice.listPrice(),
                    toPricingResponse(representativePrice, campaignNames),
                    translationLocales(translations.getOrDefault(product.getId(), Map.of())));
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return getProductById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id, String localeCode) {
        Product product = findProduct(id);
        String resolvedLocale = normalizeLocale(localeCode);
        List<ProductImage> images = productImageRepository.findByProductId(product.getId());
        Map<String, ProductTranslation> translations = loadTranslationRows(List.of(product.getId()))
                .getOrDefault(product.getId(), Map.of());
        
        String categoryName = null;
        String categorySlug = null;
        Optional<Category> categoryOpt = categoryRepository.findById(product.getCategoryId());
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            Map<String, CategoryTranslation> categoryTranslations = loadCategoryTranslationRows(
                    List.of(category.getId())).getOrDefault(category.getId(), Map.of());
            CategoryTranslation categoryTranslation = mergeCategoryTranslation(
                    category,
                    resolvedLocale,
                    categoryTranslations);
            categoryName = categoryTranslation.getName();
            categorySlug = categoryTranslation.getSlug();
        }
        
        VariantPricing representativePrice = findRepresentativePrice(product.getId());
        Map<Long, String> campaignNames = loadCampaignNames(
                representativePrice == null ? List.of() : List.of(representativePrice),
                resolvedLocale);

        return ProductResponse.fromEntity(
                product,
                mergeProductTranslation(product, resolvedLocale, translations),
                images,
                categoryName,
                categorySlug,
                representativePrice == null ? null : representativePrice.listPrice(),
                toPricingResponse(representativePrice, campaignNames),
                translationLocales(translations));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<ProductTranslation> translation = productTranslationRepository.findByLocaleCodeAndSlug(resolvedLocale, slug);
        if (translation.isEmpty() && !CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            translation = productTranslationRepository.findByLocaleCodeAndSlug(CatalogLocaleResolver.DEFAULT_LOCALE, slug);
        }
        if (translation.isEmpty()) {
            // URLs do not contain a locale prefix. Keep an existing localized slug resolvable
            // after the visitor switches language, then localize the response independently.
            translation = productTranslationRepository.findFirstBySlug(slug);
        }
        if (translation.isPresent()) {
            Product product = findProduct(translation.get().getProductId());
            return getProductById(product.getId(), resolvedLocale);
        }

        Product product = productRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return getProductById(product.getId(), resolvedLocale);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        validateUniqueProduct(request.slug());
        Product product = new Product();
        product.setSlug(request.slug());
        apply(product, request);
        Product saved = productRepository.save(product);
        ProductTranslation translation = saveDefaultTranslation(
                saved,
                request.name(),
                request.slug(),
                request.description(),
                request.description(),
                null,
                null,
                request.name(),
                request.description());
        return ProductResponse.fromEntityWithTranslationLocales(
                saved,
                translation,
                storedTranslationLocales(saved.getId()));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductWithLock(id);
        if (!java.util.Objects.equals(product.getStatus(), request.status())
                && !"ACTIVE".equals(request.status())) {
            assertNotInOutstandingCampaign(id);
        }
        product.setCategoryId(request.categoryId());
        product.setBrandId(request.brandId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setStatus(request.status());
        Product saved = productRepository.save(product);
        Optional<ProductTranslation> existingTranslation = productTranslationRepository
                .findByProductIdAndLocaleCode(saved.getId(), CatalogLocaleResolver.DEFAULT_LOCALE);
        ProductTranslation translation = existingTranslation.orElseGet(ProductTranslation::new);
        translation.setProductId(saved.getId());
        translation.setLocaleCode(CatalogLocaleResolver.DEFAULT_LOCALE);
        translation.setName(request.name());
        translation.setSlug(saved.getSlug());
        translation.setDescription(request.description());
        if (existingTranslation.isEmpty()) {
            translation.setShortDescription(request.description());
            translation.setSeoTitle(request.name());
            translation.setSeoDescription(request.description());
        }
        translation = productTranslationRepository.save(translation);
        return ProductResponse.fromEntityWithTranslationLocales(
                saved,
                translation,
                storedTranslationLocales(saved.getId()));
    }

    @Override
    @Transactional
    public ProductResponse updateStatus(Long id, UpdateStatusRequest request) {
        String status = validateStatus(
                request.status(),
                java.util.Set.of("ACTIVE", "INACTIVE"),
                "Product");
        Product product = findProductWithLock(id);
        boolean allowedTransition = (java.util.Set.of("DRAFT", "INACTIVE").contains(product.getStatus())
                        && "ACTIVE".equals(status))
                || ("ACTIVE".equals(product.getStatus()) && "INACTIVE".equals(status));
        if (!allowedTransition) {
            throw new InvalidRequestException(
                    "Product status toggle is not allowed from " + product.getStatus() + " to " + status);
        }
        if ("ACTIVE".equals(product.getStatus()) && !"ACTIVE".equals(status)) {
            assertNotInOutstandingCampaign(id);
        }
        product.setStatus(status);
        productRepository.save(product);
        return getProductById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductWithLock(id);
        assertNotInOutstandingCampaign(id);
        product.setStatus("INACTIVE");
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private Product findProductWithLock(Long id) {
        return productRepository.findWithLockByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private void assertNotInOutstandingCampaign(Long productId) {
        if (saleCampaignItemRepository.existsProtectedProduct(productId, Instant.now())) {
            throw new InvalidRequestException(
                    "Product belongs to a draft, upcoming, or live sale campaign; update that campaign first");
        }
    }

    private void validateUniqueProduct(String slug) {
        if (productRepository.existsBySlug(slug)
                || productTranslationRepository.existsByLocaleCodeAndSlug(CatalogLocaleResolver.DEFAULT_LOCALE, slug)) {
            throw new InvalidRequestException("Product slug already exists");
        }
    }

    private void apply(Product product, CreateProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setBrandId(request.brandId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setStatus(request.status());
    }

    private VariantPricing findRepresentativePrice(Long productId) {
        return loadRepresentativePrices(List.of(productId)).get(productId);
    }

    private Map<Long, String> loadCampaignNames(
            java.util.Collection<VariantPricing> pricingValues,
            String localeCode) {
        List<Long> campaignIds = pricingValues.stream()
                .filter(pricing -> pricing != null && pricing.campaignItem() != null)
                .map(pricing -> pricing.campaignItem().getCampaign().getId())
                .distinct()
                .toList();
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, SaleCampaignTranslation>> translations = saleCampaignTranslationRepository
                .findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        SaleCampaignTranslation::getCampaignId,
                        Collectors.toMap(SaleCampaignTranslation::getLocaleCode, Function.identity())));
        Map<Long, String> result = new HashMap<>();
        pricingValues.stream()
                .filter(pricing -> pricing != null && pricing.campaignItem() != null)
                .forEach(pricing -> {
                    var campaign = pricing.campaignItem().getCampaign();
                    Map<String, SaleCampaignTranslation> byLocale = translations.getOrDefault(
                            campaign.getId(),
                            Map.of());
                    SaleCampaignTranslation requested = byLocale.get(localeCode);
                    SaleCampaignTranslation defaultTranslation = byLocale.get(
                            CatalogLocaleResolver.DEFAULT_LOCALE);
                    result.put(
                            campaign.getId(),
                            firstValue(
                                    requested == null ? null : requested.getName(),
                                    defaultTranslation == null ? null : defaultTranslation.getName(),
                                    campaign.getName()));
                });
        return result;
    }

    private VariantPricingResponse toPricingResponse(
            VariantPricing pricing,
            Map<Long, String> campaignNames) {
        if (pricing == null) {
            return null;
        }
        if (pricing.campaignItem() == null) {
            return pricing.toResponse();
        }
        Long campaignId = pricing.campaignItem().getCampaign().getId();
        return pricing.toResponse(campaignNames.getOrDefault(
                campaignId,
                pricing.campaignItem().getCampaign().getName()));
    }

    private Map<Long, VariantPricing> loadRepresentativePrices(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        List<ProductVariant> variants = productVariantRepository.findByProductIdInAndDeletedAtIsNull(productIds);
        Map<Long, VariantPricing> pricingByVariant = variantPricingService.resolve(variants);
        return variants.stream()
                .filter(variant -> pricingByVariant.containsKey(variant.getId()))
                .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(variant -> pricingByVariant.get(variant.getId()))
                                .min(java.util.Comparator
                                        .comparing(VariantPricing::effectivePrice)
                                        .thenComparing(VariantPricing::variantId))
                                .orElseThrow()));
    }

    private Map<Long, Map<String, ProductTranslation>> loadTranslationRows(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productTranslationRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(
                        ProductTranslation::getProductId,
                        Collectors.toMap(ProductTranslation::getLocaleCode, Function.identity())));
    }

    private Map<Long, Map<String, CategoryTranslation>> loadCategoryTranslationRows(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return categoryTranslationRepository.findByCategoryIdIn(categoryIds).stream()
                .collect(Collectors.groupingBy(
                        CategoryTranslation::getCategoryId,
                        Collectors.toMap(CategoryTranslation::getLocaleCode, Function.identity())));
    }

    private ProductTranslation mergeProductTranslation(
            Product product,
            String localeCode,
            Map<String, ProductTranslation> translations) {
        ProductTranslation requested = translations.get(localeCode);
        ProductTranslation defaultTranslation = translations.get(CatalogLocaleResolver.DEFAULT_LOCALE);
        ProductTranslation merged = new ProductTranslation();
        merged.setProductId(product.getId());
        merged.setLocaleCode(localeCode);
        merged.setName(firstValue(
                requested == null ? null : requested.getName(),
                defaultTranslation == null ? null : defaultTranslation.getName(),
                product.getName()));
        merged.setSlug(firstValue(
                requested == null ? null : requested.getSlug(),
                defaultTranslation == null ? null : defaultTranslation.getSlug(),
                product.getSlug()));
        merged.setShortDescription(firstValue(
                requested == null ? null : requested.getShortDescription(),
                defaultTranslation == null ? null : defaultTranslation.getShortDescription(),
                product.getDescription()));
        merged.setDescription(firstValue(
                requested == null ? null : requested.getDescription(),
                defaultTranslation == null ? null : defaultTranslation.getDescription(),
                product.getDescription()));
        merged.setMaterial(firstValue(
                requested == null ? null : requested.getMaterial(),
                defaultTranslation == null ? null : defaultTranslation.getMaterial()));
        merged.setCareInstruction(firstValue(
                requested == null ? null : requested.getCareInstruction(),
                defaultTranslation == null ? null : defaultTranslation.getCareInstruction()));
        merged.setSeoTitle(firstValue(
                requested == null ? null : requested.getSeoTitle(),
                defaultTranslation == null ? null : defaultTranslation.getSeoTitle(),
                product.getName()));
        merged.setSeoDescription(firstValue(
                requested == null ? null : requested.getSeoDescription(),
                defaultTranslation == null ? null : defaultTranslation.getSeoDescription(),
                product.getDescription()));
        return merged;
    }

    private CategoryTranslation mergeCategoryTranslation(
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

    private List<String> storedTranslationLocales(Long productId) {
        return productTranslationRepository.findByProductId(productId).stream()
                .map(ProductTranslation::getLocaleCode)
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

    private String validateStatus(String status, java.util.Set<String> allowed, String resource) {
        String normalized = status.trim();
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(resource + " status is invalid: " + status);
        }
        return normalized;
    }

    private ProductTranslation saveDefaultTranslation(
            Product product,
            String name,
            String slug,
            String shortDescription,
            String description,
            String material,
            String careInstruction,
            String seoTitle,
            String seoDescription) {
        ProductTranslation translation = productTranslationRepository
                .findByProductIdAndLocaleCode(product.getId(), CatalogLocaleResolver.DEFAULT_LOCALE)
                .orElseGet(ProductTranslation::new);
        translation.setProductId(product.getId());
        translation.setLocaleCode(CatalogLocaleResolver.DEFAULT_LOCALE);
        translation.setName(name);
        translation.setSlug(slug);
        translation.setShortDescription(shortDescription);
        translation.setDescription(description);
        translation.setMaterial(material);
        translation.setCareInstruction(careInstruction);
        translation.setSeoTitle(seoTitle);
        translation.setSeoDescription(seoDescription);
        return productTranslationRepository.save(translation);
    }

    private String normalizeLocale(String localeCode) {
        if (localeCode == null || localeCode.isBlank()) {
            return CatalogLocaleResolver.DEFAULT_LOCALE;
        }
        return localeCode.trim().replace('_', '-').toLowerCase();
    }
}
