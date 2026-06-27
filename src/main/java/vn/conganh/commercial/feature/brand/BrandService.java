package vn.conganh.commercial.feature.brand;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.brand.dto.BrandFilterRequest;
import vn.conganh.commercial.feature.brand.dto.BrandResponse;
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;
import vn.conganh.commercial.feature.brand.dto.UpdateBrandRequest;

public interface BrandService {

    ResultPaginationDTO getAll(BrandFilterRequest filter, Pageable pageable);

    BrandResponse getById(Long id);

    BrandResponse create(CreateBrandRequest request);

    BrandResponse update(Long id, UpdateBrandRequest request);

    void delete(Long id);
}
