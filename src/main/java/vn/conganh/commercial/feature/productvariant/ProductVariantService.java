package vn.conganh.commercial.feature.productvariant;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantFilterRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;

public interface ProductVariantService {

    ResultPaginationDTO getAll(ProductVariantFilterRequest filter, Pageable pageable);

    ProductVariantResponse getById(Long id);

    ProductVariantResponse create(CreateProductVariantRequest request);

    ProductVariantResponse update(Long id, UpdateProductVariantRequest request);

    void delete(Long id);
}
