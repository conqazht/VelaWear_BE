package vn.conganh.commercial.feature.color;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.color.dto.ColorResponse;
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;
import vn.conganh.commercial.feature.color.dto.UpdateColorRequest;

public interface ColorService {

    ResultPaginationDTO getAll(Pageable pageable);

    ColorResponse getById(Long id);

    ColorResponse create(CreateColorRequest request);

    ColorResponse update(Long id, UpdateColorRequest request);

    void delete(Long id);
}
