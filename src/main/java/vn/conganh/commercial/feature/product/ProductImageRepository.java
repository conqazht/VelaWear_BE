package vn.conganh.commercial.feature.product;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @EntityGraph(attributePaths = {"product", "variant", "variant.color"})
    List<ProductImage> findByProductId(Long productId);

    @EntityGraph(attributePaths = {"product", "variant", "variant.color"})
    List<ProductImage> findByProductIdIn(List<Long> productIds);

    @EntityGraph(attributePaths = {"product", "variant", "variant.color"})
    List<ProductImage> findByVariantId(Long variantId);
}
