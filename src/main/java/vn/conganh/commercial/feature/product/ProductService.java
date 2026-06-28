package vn.conganh.commercial.feature.product;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductFilterRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;

public interface ProductService {

    ResultPaginationDTO getAllProducts(ProductFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getAllProducts(ProductFilterRequest filter, Pageable pageable, String localeCode);

    ProductResponse getProductById(Long id);

    ProductResponse getProductById(Long id, String localeCode);

    ProductResponse getProductBySlug(String slug, String localeCode);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);
}
