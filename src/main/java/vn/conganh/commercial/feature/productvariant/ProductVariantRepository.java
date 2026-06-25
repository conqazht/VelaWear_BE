package vn.conganh.commercial.feature.productvariant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findByIdAndDeletedAtIsNull(Long id);

    List<ProductVariant> findAllByDeletedAtIsNull();

    boolean existsBySku(String sku);
}
