package vn.conganh.commercial.feature.size;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

@Service
@RequiredArgsConstructor
public class SizeServiceImpl implements SizeService {

    private final SizeRepository sizeRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SizeResponse> getAll() {
        return sizeRepository.findAll().stream()
                .map(SizeResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SizeResponse getById(Long id) {
        return SizeResponse.fromEntity(findById(id));
    }

    @Override
    @Transactional
    public SizeResponse create(CreateSizeRequest request) {
        if (sizeRepository.existsByName(request.name())) {
            throw new InvalidRequestException("Size name already exists");
        }
        Size size = new Size();
        size.setName(request.name());
        size.setSortOrder(request.sortOrder());
        return SizeResponse.fromEntity(sizeRepository.save(size));
    }

    @Override
    @Transactional
    public SizeResponse update(Long id, UpdateSizeRequest request) {
        Size size = findById(id);
        if (!size.getName().equals(request.name()) && sizeRepository.existsByName(request.name())) {
            throw new InvalidRequestException("Size name already exists");
        }
        size.setName(request.name());
        size.setSortOrder(request.sortOrder());
        return SizeResponse.fromEntity(sizeRepository.save(size));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        sizeRepository.deleteById(id);
    }

    private Size findById(Long id) {
        return sizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size", "id", id));
    }
}
