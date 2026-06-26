package vn.conganh.commercial.feature.product;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getAllProducts(Pageable pageable) {
        return ResultPaginationDTO.fromPage(productRepository.findAllByDeletedAtIsNull(pageable)
                .map(ProductResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return ProductResponse.fromEntity(findProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        validateUniqueProduct(request.slug());
        Product product = new Product();
        product.setSlug(request.slug());
        apply(product, request);
        return ProductResponse.fromEntity(productRepository.save(product));
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
        return ProductResponse.fromEntity(productRepository.save(product));
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
        if (productRepository.existsBySlug(slug)) {
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
}
