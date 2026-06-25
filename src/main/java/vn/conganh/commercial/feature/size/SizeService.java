package vn.conganh.commercial.feature.size;

import java.util.List;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

public interface SizeService {

    List<SizeResponse> getAll();

    SizeResponse getById(Long id);

    SizeResponse create(CreateSizeRequest request);

    SizeResponse update(Long id, UpdateSizeRequest request);

    void delete(Long id);
}
