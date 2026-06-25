package vn.conganh.commercial.feature.brand;

import java.util.List;
import vn.conganh.commercial.feature.brand.dto.BrandResponse;
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;
import vn.conganh.commercial.feature.brand.dto.UpdateBrandRequest;

public interface BrandService {

    List<BrandResponse> getAll();

    BrandResponse getById(Long id);

    BrandResponse create(CreateBrandRequest request);

    BrandResponse update(Long id, UpdateBrandRequest request);

    void delete(Long id);
}
