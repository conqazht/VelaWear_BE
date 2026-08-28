package vn.conganh.commercial.feature.product;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleHelper;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.product.dto.ProductTranslationRequest;
import vn.conganh.commercial.feature.product.dto.ProductTranslationResponse;
import vn.conganh.commercial.feature.product.dto.ProductTranslationsResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductTranslationsRequest;

@Service
@RequiredArgsConstructor
public class ProductTranslationServiceImpl implements ProductTranslationService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository translationRepository;
    private final CatalogLocaleResolver localeResolver;

    @Override
    @Transactional(readOnly = true)
    public ProductTranslationsResponse getTranslations(Long productId) {
        findProduct(productId);
        return response(productId);
    }

    @Override
    @Transactional
    public ProductTranslationsResponse updateTranslations(
            Long productId,
            UpdateProductTranslationsRequest request) {
        Product product = findProductWithLock(productId);
        Set<String> localeCodes = new HashSet<>();
        for (ProductTranslationRequest item : request.translations()) {
            String localeCode = localeResolver.requireEnabledLocale(item.localeCode());
            if (!localeCodes.add(localeCode)) {
                throw new InvalidRequestException("Duplicate locale in translation batch: " + localeCode);
            }
            validateSlug(productId, localeCode, item.slug());
            ProductTranslation translation = translationRepository
                    .findByProductIdAndLocaleCode(productId, localeCode)
                    .orElseGet(ProductTranslation::new);
            apply(translation, productId, localeCode, item);
            translationRepository.save(translation);
            if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(localeCode)) {
                validateCoreSlug(productId, item.slug());
                product.setName(item.name().trim());
                product.setSlug(item.slug().trim());
                product.setDescription(item.description());
            }
        }
        productRepository.save(product);
        translationRepository.flush();
        return response(productId);
    }

    @Override
    @Transactional
    public void deleteTranslation(Long productId, String localeCode) {
        findProductWithLock(productId);
        String resolvedLocale = localeResolver.requireEnabledLocale(localeCode);
        CatalogLocaleHelper.assertNotDefaultLocale(resolvedLocale);
        ProductTranslation translation = translationRepository
                .findByProductIdAndLocaleCode(productId, resolvedLocale)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProductTranslation", "localeCode", resolvedLocale));
        translationRepository.delete(translation);
    }

    private void apply(
            ProductTranslation translation,
            Long productId,
            String localeCode,
            ProductTranslationRequest request) {
        translation.setProductId(productId);
        translation.setLocaleCode(localeCode);
        translation.setName(request.name().trim());
        translation.setSlug(request.slug().trim());
        translation.setShortDescription(request.shortDescription());
        translation.setDescription(request.description());
        translation.setMaterial(request.material());
        translation.setCareInstruction(request.careInstruction());
        translation.setSeoTitle(request.seoTitle());
        translation.setSeoDescription(request.seoDescription());
    }

    private void validateSlug(Long productId, String localeCode, String slug) {
        Optional<ProductTranslation> owner = translationRepository
                .findByLocaleCodeAndSlug(localeCode, slug.trim());
        if (owner.isPresent() && !owner.get().getProductId().equals(productId)) {
            throw new InvalidRequestException("Product translation slug already exists for locale " + localeCode);
        }
    }

    private void validateCoreSlug(Long productId, String slug) {
        Optional<Product> owner = productRepository.findBySlug(slug.trim());
        if (owner.isPresent() && !owner.get().getId().equals(productId)) {
            throw new InvalidRequestException("Product slug already exists");
        }
    }

    private ProductTranslationsResponse response(Long productId) {
        List<ProductTranslationResponse> translations = translationRepository.findByProductId(productId).stream()
                .sorted((left, right) -> CatalogLocaleHelper.compareLocales(left.getLocaleCode(), right.getLocaleCode()))
                .map(ProductTranslationResponse::fromEntity)
                .toList();
        return new ProductTranslationsResponse(translations);
    }

    private Product findProduct(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }

    private Product findProductWithLock(Long productId) {
        return productRepository.findWithLockByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    }
}
