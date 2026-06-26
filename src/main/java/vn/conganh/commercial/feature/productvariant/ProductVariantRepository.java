package vn.conganh.commercial.feature.productvariant;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductVariant> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySku(String sku);
}
