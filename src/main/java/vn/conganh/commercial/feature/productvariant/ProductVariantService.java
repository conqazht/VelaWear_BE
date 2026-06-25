package vn.conganh.commercial.feature.productvariant;

import java.util.List;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;

public interface ProductVariantService {

    List<ProductVariantResponse> getAll();

    ProductVariantResponse getById(Long id);

    ProductVariantResponse create(CreateProductVariantRequest request);

    ProductVariantResponse update(Long id, UpdateProductVariantRequest request);

    void delete(Long id);
}
