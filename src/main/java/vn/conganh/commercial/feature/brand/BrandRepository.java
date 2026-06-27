package vn.conganh.commercial.feature.brand;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BrandRepository extends JpaRepository<Brand, Long>, JpaSpecificationExecutor<Brand> {

    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);

    Page<Brand> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySlug(String slug);
}
