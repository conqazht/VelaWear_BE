package vn.conganh.commercial.feature.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findByProductIdAndName(Long productId, String name);

    List<ProductAttribute> findByProductId(Long productId);

    boolean existsByProductIdAndName(Long productId, String name);
}
