package vn.conganh.commercial.feature.productvariant;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
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

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;
    private final VariantPricingService variantPricingService;
    private final SaleCampaignItemRepository saleCampaignItemRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(ProductVariantFilterRequest filter, Pageable pageable) {
        var page = productVariantRepository.findAll(Specification.where(ProductVariantSpecification.build(filter)), pageable);
        var pricing = variantPricingService.resolve(page.getContent());
        return ResultPaginationDTO.fromPage(page.map(variant -> ProductVariantResponse.fromEntity(
                variant, pricing.get(variant.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getById(Long id) {
        ProductVariant variant = findActive(id);
        return ProductVariantResponse.fromEntity(variant,
                variantPricingService.resolve(java.util.List.of(variant)).get(variant.getId()));
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
