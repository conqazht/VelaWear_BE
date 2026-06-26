package vn.conganh.commercial.feature.productvariant;

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
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;
import vn.conganh.commercial.feature.size.Size;
import vn.conganh.commercial.feature.size.SizeRepository;

@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ColorRepository colorRepository;
    private final SizeRepository sizeRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(Pageable pageable) {
        return ResultPaginationDTO.fromPage(productVariantRepository.findAllByDeletedAtIsNull(pageable)
                .map(ProductVariantResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getById(Long id) {
        return ProductVariantResponse.fromEntity(findActive(id));
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
        variant.setSalePrice(request.salePrice());
        variant.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        variant.setColor(color);
        variant.setSize(size);
        variant.setStatus(request.status() != null ? request.status() : "ACTIVE");

        return ProductVariantResponse.fromEntity(productVariantRepository.save(variant));
    }

    @Override
    @Transactional
    public ProductVariantResponse update(Long id, UpdateProductVariantRequest request) {
        ProductVariant variant = findActive(id);

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
        variant.setSalePrice(request.salePrice());
        variant.setStockQuantity(request.stockQuantity() != null ? request.stockQuantity() : 0);
        variant.setColor(color);
        variant.setSize(size);
        variant.setStatus(request.status());

        return ProductVariantResponse.fromEntity(productVariantRepository.save(variant));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ProductVariant variant = findActive(id);
        variant.setDeletedAt(Instant.now());
        productVariantRepository.save(variant);
    }

    private ProductVariant findActive(Long id) {
        return productVariantRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "id", id));
    }
}
