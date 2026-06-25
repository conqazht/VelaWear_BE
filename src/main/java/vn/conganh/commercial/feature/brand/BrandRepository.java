package vn.conganh.commercial.feature.brand;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByIdAndDeletedAtIsNull(Long id);

    List<Brand> findAllByDeletedAtIsNull();

    boolean existsBySlug(String slug);
}
