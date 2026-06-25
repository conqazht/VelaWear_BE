package vn.conganh.commercial.feature.color;

import java.util.List;
import vn.conganh.commercial.feature.color.dto.ColorResponse;
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;
import vn.conganh.commercial.feature.color.dto.UpdateColorRequest;

public interface ColorService {

    List<ColorResponse> getAll();

    ColorResponse getById(Long id);

    ColorResponse create(CreateColorRequest request);

    ColorResponse update(Long id, UpdateColorRequest request);

    void delete(Long id);
}
