package vn.conganh.commercial.feature.size;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.size.dto.CreateSizeRequest;
import vn.conganh.commercial.feature.size.dto.SizeFilterRequest;
import vn.conganh.commercial.feature.size.dto.SizeResponse;
import vn.conganh.commercial.feature.size.dto.UpdateSizeRequest;

@Service
@RequiredArgsConstructor
public class SizeService {

    private final SizeRepository sizeRepository;

    @Transactional(readOnly = true)
    public ResultPaginationDTO getAll(SizeFilterRequest filter, Pageable pageable) {
        return ResultPaginationDTO.fromPage(sizeRepository.findAll(Specification.where(SizeSpecification.build(filter)), pageable)
                .map(SizeResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public SizeResponse getById(Long id) {
        return SizeResponse.fromEntity(findById(id));
    }

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

    @Transactional
    public void delete(Long id) {
        sizeRepository.delete(findById(id));
    }

    private Size findById(Long id) {
        return sizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size", "id", id));
    }
}
