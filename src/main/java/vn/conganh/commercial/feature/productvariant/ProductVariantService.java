package vn.conganh.commercial.feature.productvariant;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.dto.UpdateStatusRequest;
import vn.conganh.commercial.feature.productvariant.dto.CreateProductVariantRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantFilterRequest;
import vn.conganh.commercial.feature.productvariant.dto.ProductVariantResponse;
import vn.conganh.commercial.feature.productvariant.dto.UpdateProductVariantRequest;

public interface ProductVariantService {

    ResultPaginationDTO getAll(ProductVariantFilterRequest filter, Pageable pageable);

    ResultPaginationDTO getAll(ProductVariantFilterRequest filter, Pageable pageable, String localeCode);

    ProductVariantResponse getById(Long id);

    ProductVariantResponse getById(Long id, String localeCode);

    ProductVariantResponse create(CreateProductVariantRequest request);

    ProductVariantResponse update(Long id, UpdateProductVariantRequest request);

    ProductVariantResponse updateStatus(Long id, UpdateStatusRequest request);

    void delete(Long id);
}
