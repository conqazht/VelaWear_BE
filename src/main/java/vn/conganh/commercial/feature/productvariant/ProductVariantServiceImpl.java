package vn.conganh.commercial.feature.productvariant;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.catalog.i18n.CatalogLocaleResolver;
import vn.conganh.commercial.feature.color.Color;
import vn.conganh.commercial.feature.color.ColorRepository;
import vn.conganh.commercial.feature.product.ProductRepository;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantFilterRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.size.SizeRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricingService;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItemRepository;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslationRepository;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final VariantPricingService variantPricingService;
    private final SaleCampaignItemRepository saleCampaignItemRepository;
    private final SaleCampaignTranslationRepository saleCampaignTranslationRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(ProductVariantFilterRequest filter, Pageable pageable) {
        return getAll(filter, pageable, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(
            ProductVariantFilterRequest filter,
            Pageable pageable,
            String localeCode) {
        var page = productVariantRepository.findAll(Specification.where(ProductVariantSpecification.build(filter)), pageable);
        var pricing = variantPricingService.resolve(page.getContent());
        Map<Long, String> campaignNames = loadCampaignNames(pricing.values(), localeCode);
        return ResultPaginationDTO.fromPage(page.map(variant -> ProductVariantResponse.fromEntity(
                variant,
                pricing.get(variant.getId()),
                campaignName(pricing.get(variant.getId()), campaignNames))));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getById(Long id) {
        return getById(id, CatalogLocaleResolver.DEFAULT_LOCALE);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getById(Long id, String localeCode) {
        ProductVariant variant = findActive(id);
        VariantPricing pricing = variantPricingService.resolve(List.of(variant)).get(variant.getId());
        Map<Long, String> campaignNames = loadCampaignNames(
                pricing == null ? List.of() : List.of(pricing),
                localeCode);
        return ProductVariantResponse.fromEntity(
                variant,
                pricing,
                campaignName(pricing, campaignNames));
    }

    @Override
    @Transactional
    public ProductVariantResponse create(CreateProductVariantRequest request) {
        if (productVariantRepository.existsBySku(request.sku())) {
            throw new InvalidRequestException("Product variant sku already exists");
        }

        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.productId()));

        Color color = null;
        if (request.colorId() != null) {
            color = colorRepository.findById(request.colorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Color", "id", request.colorId()));
        }

        Size size = null;
        if (request.sizeId() != null) {
            size = sizeRepository.findById(request.sizeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Size", "id", request.sizeId()));
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(request.sku());
        variant.setPrice(request.price());
        variant.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        variant.setColor(color);
        variant.setSize(size);
        variant.setStatus(request.status() != null ? request.status() : "ACTIVE");

        ProductVariant saved = productVariantRepository.save(variant);
        return ProductVariantResponse.fromEntity(saved, resolvePricing(saved));
    }

    @Override
    @Transactional
    public ProductVariantResponse update(Long id, UpdateProductVariantRequest request) {
        // Serialize an absolute stock update with checkout's conditional stock decrement.
        // Without the row lock, a stale managed entity could overwrite a concurrent checkout.
        ProductVariant variant = findActiveWithLock(id);
        if (hasCampaignSensitiveChange(variant, request)) {
            assertNotInScheduledCampaign(id);
        }

        if (!variant.getSku().equals(request.sku()) && productVariantRepository.existsBySku(request.sku())) {
            throw new InvalidRequestException("Product variant sku already exists");
        }

        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.productId()));

        Color color = null;
        if (request.colorId() != null) {
            color = colorRepository.findById(request.colorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Color", "id", request.colorId()));
        }

        Size size = null;
        if (request.sizeId() != null) {
            size = sizeRepository.findById(request.sizeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Size", "id", request.sizeId()));
        }

        variant.setProduct(product);
        variant.setSku(request.sku());
        variant.setPrice(request.price());
        variant.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        variant.setColor(color);
        variant.setSize(size);
        variant.setStatus(request.status());

        ProductVariant saved = productVariantRepository.save(variant);
        return ProductVariantResponse.fromEntity(saved, resolvePricing(saved));
    }

    @Override
    @Transactional
    public ProductVariantResponse updateStatus(Long id, UpdateStatusRequest request) {
        String status = request.status().trim();
        if (!java.util.Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new InvalidRequestException("ProductVariant status is invalid: " + request.status());
        }
        ProductVariant variant = findActiveWithLock(id);
        boolean allowedTransition = ("ACTIVE".equals(variant.getStatus()) && "INACTIVE".equals(status))
                || ("INACTIVE".equals(variant.getStatus()) && "ACTIVE".equals(status));
        if (!allowedTransition) {
            throw new InvalidRequestException(
                    "ProductVariant status toggle is not allowed from " + variant.getStatus() + " to " + status);
        }
        if ("ACTIVE".equals(variant.getStatus()) && !"ACTIVE".equals(status)) {
            assertNotInScheduledCampaign(id);
        }
        variant.setStatus(status);
        ProductVariant saved = productVariantRepository.save(variant);
        return ProductVariantResponse.fromEntity(saved, resolvePricing(saved));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ProductVariant variant = findActiveWithLock(id);
        assertNotInScheduledCampaign(id);
        variant.setDeletedAt(Instant.now());
        productVariantRepository.save(variant);
    }

    private ProductVariant findActive(Long id) {
        return productVariantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "id", id));
    }

    private ProductVariant findActiveWithLock(Long id) {
        return productVariantRepository.findWithLockByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "id", id));
    }

    private vn.conganh.commercial.feature.salecampaign.VariantPricing resolvePricing(ProductVariant variant) {
        if (variant.getId() == null) {
            return null;
        }
        var pricing = variantPricingService.resolve(java.util.List.of(variant));
        return pricing == null ? null : pricing.get(variant.getId());
    }

    private Map<Long, String> loadCampaignNames(
            Collection<VariantPricing> pricingValues,
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

    private String campaignName(VariantPricing pricing, Map<Long, String> campaignNames) {
        if (pricing == null || pricing.campaignItem() == null) {
            return null;
        }
        var campaign = pricing.campaignItem().getCampaign();
        return campaignNames.getOrDefault(campaign.getId(), campaign.getName());
    }

    private String firstValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void assertNotInScheduledCampaign(Long variantId) {
        if (saleCampaignItemRepository.existsProtectedVariant(variantId, Instant.now())) {
            throw new InvalidRequestException(
                    "Variant belongs to a draft, upcoming, or live sale campaign; remove it from that campaign first");
        }
    }

    private boolean hasCampaignSensitiveChange(ProductVariant variant, UpdateProductVariantRequest request) {
        return !java.util.Objects.equals(variant.getProduct().getId(), request.productId())
                || !java.util.Objects.equals(variant.getSku(), request.sku())
                || variant.getPrice().compareTo(request.price()) != 0
                || !java.util.Objects.equals(
                        variant.getColor() == null ? null : variant.getColor().getId(), request.colorId())
                || !java.util.Objects.equals(
                        variant.getSize() == null ? null : variant.getSize().getId(), request.sizeId())
                || !java.util.Objects.equals(variant.getStatus(), request.status());
    }
}
