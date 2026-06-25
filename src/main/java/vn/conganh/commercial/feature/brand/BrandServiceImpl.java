package vn.conganh.commercial.feature.brand;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.brand.dto.BrandResponse;
import vn.conganh.commercial.feature.brand.dto.CreateBrandRequest;
import vn.conganh.commercial.feature.brand.dto.UpdateBrandRequest;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BrandResponse> getAll() {
        return brandRepository.findAllByDeletedAtIsNull().stream()
                .map(BrandResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse getById(Long id) {
        return BrandResponse.fromEntity(findActive(id));
    }

    @Override
    @Transactional
    public BrandResponse create(CreateBrandRequest request) {
        if (brandRepository.existsBySlug(request.slug())) {
            throw new InvalidRequestException("Brand slug already exists");
        }
        Brand brand = new Brand();
        brand.setName(request.name());
        brand.setSlug(request.slug());
        brand.setDescription(request.description());
        brand.setStatus(request.status() != null ? request.status() : "ACTIVE");
        return BrandResponse.fromEntity(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public BrandResponse update(Long id, UpdateBrandRequest request) {
        Brand brand = findActive(id);
        brand.setName(request.name());
        brand.setDescription(request.description());
        brand.setStatus(request.status());
        return BrandResponse.fromEntity(brandRepository.save(brand));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Brand brand = findActive(id);
        brand.setDeletedAt(Instant.now());
        brandRepository.save(brand);
    }

    private Brand findActive(Long id) {
        return brandRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand", "id", id));
    }
}
