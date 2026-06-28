package vn.conganh.commercial.feature.catalog.i18n;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogLocaleRepository extends JpaRepository<CatalogLocale, String> {

    Optional<CatalogLocale> findByCodeAndEnabledTrue(String code);

    Optional<CatalogLocale> findFirstByDefaultLocaleTrueAndEnabledTrue();
}
