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
import vn.conganh.commercial.feature.productvariant.ProductVariantRepository.RepresentativePrice;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTranslationRepository categoryTranslationRepository;
    private final ProductVariantRepository productVariantRepository;

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
        Map<Long, ProductTranslation> translations = loadTranslations(
                products.getContent().stream().map(Product::getId).toList(),
                resolvedLocale);

        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        List<ProductImage> allImages = productImageRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductImage>> imagesMap = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProduct().getId()));

        List<Long> categoryIds = products.getContent().stream().map(Product::getCategoryId).distinct().toList();
        List<Category> allCategories = categoryRepository.findAllById(categoryIds);
        Map<Long, Category> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        
        List<CategoryTranslation> catTranslations = categoryTranslationRepository.findByCategoryIdInAndLocaleCode(categoryIds, resolvedLocale);
        Map<Long, CategoryTranslation> catTranslationMap = catTranslations.stream()
                .collect(Collectors.toMap(CategoryTranslation::getCategoryId, Function.identity()));

        Map<Long, RepresentativePrice> representativePrices = loadRepresentativePrices(productIds);

        return ResultPaginationDTO.fromPage(products.map(product -> {
            Category category = categoryMap.get(product.getCategoryId());
            CategoryTranslation catTrans = category == null ? null : catTranslationMap.get(category.getId());
            String catName = catTrans != null ? catTrans.getName() : (category != null ? category.getName() : null);
            String catSlug = category != null ? category.getSlug() : null;
            
            RepresentativePrice representativePrice = representativePrices.get(product.getId());

            return ProductResponse.fromEntity(
                    product,
                    translations.get(product.getId()),
                    imagesMap.get(product.getId()),
                    catName,
                    catSlug,
                    representativePrice == null ? null : representativePrice.getPrice(),
                    representativePrice == null ? null : representativePrice.getSalePrice());
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
        
        String categoryName = null;
        String categorySlug = null;
        Optional<Category> categoryOpt = categoryRepository.findById(product.getCategoryId());
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            categorySlug = category.getSlug();
            Optional<CategoryTranslation> catTranslation = categoryTranslationRepository.findByCategoryIdAndLocaleCode(category.getId(), resolvedLocale);
            categoryName = catTranslation.map(CategoryTranslation::getName).orElse(category.getName());
        }
        
        RepresentativePrice representativePrice = findRepresentativePrice(product.getId());

        return ProductResponse.fromEntity(
                product,
                resolveTranslation(product.getId(), localeCode).orElse(null),
                images,
                categoryName,
                categorySlug,
                representativePrice == null ? null : representativePrice.getPrice(),
                representativePrice == null ? null : representativePrice.getSalePrice());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<ProductTranslation> translation = productTranslationRepository.findByLocaleCodeAndSlug(resolvedLocale, slug);
        if (translation.isEmpty() && !CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            translation = productTranslationRepository.findByLocaleCodeAndSlug(CatalogLocaleResolver.DEFAULT_LOCALE, slug);
        }
        if (translation.isPresent()) {
            Product product = findProduct(translation.get().getProductId());
            List<ProductImage> images = productImageRepository.findByProductId(product.getId());
            
            String categoryName = null;
            String categorySlug = null;
            Optional<Category> categoryOpt = categoryRepository.findById(product.getCategoryId());
            if (categoryOpt.isPresent()) {
                Category category = categoryOpt.get();
                categorySlug = category.getSlug();
                Optional<CategoryTranslation> catTranslation = categoryTranslationRepository.findByCategoryIdAndLocaleCode(category.getId(), resolvedLocale);
                categoryName = catTranslation.map(CategoryTranslation::getName).orElse(category.getName());
            }
            
            RepresentativePrice representativePrice = findRepresentativePrice(product.getId());

            return ProductResponse.fromEntity(
                    product,
                    resolveTranslation(product.getId(), resolvedLocale).orElse(null),
                    images,
                    categoryName,
                    categorySlug,
                    representativePrice == null ? null : representativePrice.getPrice(),
                    representativePrice == null ? null : representativePrice.getSalePrice());
        }

        Product product = productRepository.findBySlugAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        List<ProductImage> images = productImageRepository.findByProductId(product.getId());
        
        String categoryName = null;
        String categorySlug = null;
        Optional<Category> categoryOpt = categoryRepository.findById(product.getCategoryId());
        if (categoryOpt.isPresent()) {
            Category category = categoryOpt.get();
            categorySlug = category.getSlug();
            Optional<CategoryTranslation> catTranslation = categoryTranslationRepository.findByCategoryIdAndLocaleCode(category.getId(), resolvedLocale);
            categoryName = catTranslation.map(CategoryTranslation::getName).orElse(category.getName());
        }
        
        RepresentativePrice representativePrice = findRepresentativePrice(product.getId());

        return ProductResponse.fromEntity(
                product,
                null,
                images,
                categoryName,
                categorySlug,
                representativePrice == null ? null : representativePrice.getPrice(),
                representativePrice == null ? null : representativePrice.getSalePrice());
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
        return ProductResponse.fromEntity(saved, translation);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProduct(id);
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
        return ProductResponse.fromEntity(saved, translation);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        product.setStatus("INACTIVE");
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
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

    private Optional<ProductTranslation> resolveTranslation(Long productId, String localeCode) {
        String resolvedLocale = normalizeLocale(localeCode);
        Optional<ProductTranslation> translation =
                productTranslationRepository.findByProductIdAndLocaleCode(productId, resolvedLocale);
        if (translation.isPresent() || CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            return translation;
        }
        return productTranslationRepository.findByProductIdAndLocaleCode(productId, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    private RepresentativePrice findRepresentativePrice(Long productId) {
        return loadRepresentativePrices(List.of(productId)).get(productId);
    }

    private Map<Long, RepresentativePrice> loadRepresentativePrices(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productVariantRepository.findRepresentativePricesByProductIds(productIds).stream()
                .collect(Collectors.toMap(RepresentativePrice::getProductId, Function.identity()));
    }

    private Map<Long, ProductTranslation> loadTranslations(List<Long> productIds, String localeCode) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ProductTranslation> translations = new HashMap<>(productTranslationRepository
                .findByProductIdInAndLocaleCode(productIds, CatalogLocaleResolver.DEFAULT_LOCALE)
                .stream()
                .collect(Collectors.toMap(ProductTranslation::getProductId, Function.identity())));

        String resolvedLocale = normalizeLocale(localeCode);
        if (!CatalogLocaleResolver.DEFAULT_LOCALE.equals(resolvedLocale)) {
            productTranslationRepository.findByProductIdInAndLocaleCode(productIds, resolvedLocale)
                    .forEach(translation -> translations.put(translation.getProductId(), translation));
        }
        return translations;
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
