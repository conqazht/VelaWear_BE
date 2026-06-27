package vn.conganh.commercial.feature.size;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeFilterRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

public interface SizeService {

    ResultPaginationDTO getAll(SizeFilterRequest filter, Pageable pageable);

    SizeResponse getById(Long id);

    SizeResponse create(CreateSizeRequest request);

    SizeResponse update(Long id, UpdateSizeRequest request);

    void delete(Long id);
}
