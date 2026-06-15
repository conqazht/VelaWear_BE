package vn.conganh.commercial.feature.product;

import java.util.List;
import java.util.UUID;
import vn.conganh.commercial.feature.product.dto.CreateProductRequest;
import vn.conganh.commercial.feature.product.dto.ProductResponse;
import vn.conganh.commercial.feature.product.dto.UpdateProductRequest;

public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(UUID id);

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse updateProduct(UUID id, UpdateProductRequest request);

    void deleteProduct(UUID id);
}
