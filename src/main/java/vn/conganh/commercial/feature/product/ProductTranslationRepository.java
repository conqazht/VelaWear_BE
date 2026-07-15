package vn.conganh.commercial.feature.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, ProductTranslationId> {

    boolean existsByLocaleCodeAndSlug(String localeCode, String slug);

    Optional<ProductTranslation> findByProductIdAndLocaleCode(Long productId, String localeCode);

    Optional<ProductTranslation> findByLocaleCodeAndSlug(String localeCode, String slug);

    List<ProductTranslation> findByProductIdInAndLocaleCode(Collection<Long> productIds, String localeCode);

    List<ProductTranslation> findByProductId(Long productId);

    List<ProductTranslation> findByProductIdIn(Collection<Long> productIds);
}
