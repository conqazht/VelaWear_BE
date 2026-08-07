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
    private final ProductResponseAssembler responseAssembler;

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
            Map<String, CategoryTranslation> catTranslations = category == null
                    ? Map.of()
                    : categoryTranslations.getOrDefault(category.getId(), Map.of());
            VariantPricing representativePrice = representativePrices.get(product.getId());

            return responseAssembler.assemble(
                    product,
                    resolvedLocale,
                    translations.getOrDefault(product.getId(), Map.of()),
                    imagesMap.get(product.getId()),
                    category,
                    catTranslations,
                    representativePrice,
                    campaignNames);
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

        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        Map<String, CategoryTranslation> categoryTranslations = category == null
                ? Map.of()
                : loadCategoryTranslationRows(List.of(category.getId()))
                        .getOrDefault(category.getId(), Map.of());

        VariantPricing representativePrice = findRepresentativePrice(product.getId());
        Map<Long, String> campaignNames = loadCampaignNames(
                representativePrice == null ? List.of() : List.of(representativePrice),
                resolvedLocale);

        return responseAssembler.assemble(
                product,
                resolvedLocale,
                translations,
                images,
                category,
                categoryTranslations,
                representativePrice,
                campaignNames);
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

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<String> storedTranslationLocales(Long productId) {
        return productTranslationRepository.findByProductId(productId).stream()
                .map(ProductTranslation::getLocaleCode)
                .distinct()
                .sorted(responseAssembler::compareLocales)
                .toList();
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
