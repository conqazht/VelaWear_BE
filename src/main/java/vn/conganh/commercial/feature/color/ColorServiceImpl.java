package vn.conganh.commercial.feature.color;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.color.dto.ColorResponse;
import vn.conganh.commercial.feature.color.dto.CreateColorRequest;
import vn.conganh.commercial.feature.color.dto.UpdateColorRequest;

@Service
@RequiredArgsConstructor
public class ColorServiceImpl implements ColorService {

    private final ColorRepository colorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ColorResponse> getAll() {
        return colorRepository.findAll().stream()
                .map(ColorResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ColorResponse getById(Long id) {
        return ColorResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public ColorResponse create(CreateColorRequest request) {
        if (colorRepository.existsByName(request.name())) {
            throw new InvalidRequestException("Color name already exists");
        }
        Color color = new Color();
        color.setName(request.name());
        color.setHexCode(request.hexCode());
        color.setSortOrder(request.sortOrder());
        return ColorResponse.fromEntity(colorRepository.save(color));
    }

    @Override
    @Transactional
    public ColorResponse update(Long id, UpdateColorRequest request) {
        Color color = findById(id);
        if (!color.getName().equals(request.name()) && colorRepository.existsByName(request.name())) {
            throw new InvalidRequestException("Color name already exists");
        }
        color.setName(request.name());
        color.setHexCode(request.hexCode());
        color.setSortOrder(request.sortOrder());
        return ColorResponse.fromEntity(colorRepository.save(color));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        colorRepository.deleteById(id);
    }

    private Color findById(Long id) {
        return colorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Color", "id", id));
    }
}
