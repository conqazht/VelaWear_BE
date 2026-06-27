package vn.conganh.commercial.feature.productvariant;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductVariantRepository
        extends JpaRepository<ProductVariant, Long>, JpaSpecificationExecutor<ProductVariant> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductVariant> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsBySku(String sku);
}
