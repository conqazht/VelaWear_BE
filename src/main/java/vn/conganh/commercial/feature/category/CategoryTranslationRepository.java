package vn.conganh.commercial.feature.category;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, CategoryTranslationId> {

    boolean existsByLocaleCodeAndSlug(String localeCode, String slug);

    Optional<CategoryTranslation> findByCategoryIdAndLocaleCode(Long categoryId, String localeCode);

    Optional<CategoryTranslation> findByLocaleCodeAndSlug(String localeCode, String slug);

    List<CategoryTranslation> findByCategoryIdInAndLocaleCode(Collection<Long> categoryIds, String localeCode);
}
