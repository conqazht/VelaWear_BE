package vn.conganh.commercial.feature.product;

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
import vn.conganh.commercial.feature.catalog.CatalogDisplayService;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final SaleCampaignItemRepository saleCampaignItemRepository;
    private final CatalogDisplayService catalogDisplayService;

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
        List<ProductResponse> responses = catalogDisplayService.assembleProductResponses(products.getContent(), resolvedLocale);
        Map<Long, ProductResponse> responseMap = responses.stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));
        return ResultPaginationDTO.fromPage(products.map(p -> responseMap.get(p.getId())));
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
        return catalogDisplayService.assembleProductResponse(product, normalizeLocale(localeCode));
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
            return catalogDisplayService.assembleProductResponse(product, resolvedLocale);
        }

        Product product = productRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return catalogDisplayService.assembleProductResponse(product, resolvedLocale);
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

    private List<String> storedTranslationLocales(Long productId) {
        return productTranslationRepository.findByProductId(productId).stream()
                .map(ProductTranslation::getLocaleCode)
                .distinct()
                .sorted(catalogDisplayService::compareLocales)
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
