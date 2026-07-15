package vn.conganh.commercial.feature.catalog.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogLocaleResolver")
class CatalogLocaleResolverTest {

    @Mock
    private CatalogLocaleRepository localeRepository;

    private CatalogLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CatalogLocaleResolver(localeRepository);
    }

    @Test
    @DisplayName("resolve - ưu tiên locale query parameter khi được hỗ trợ")
    void resolve_supportedExplicitLocale_returnsExplicitLocale() {
        when(localeRepository.findByCodeAndEnabledTrue("vi")).thenReturn(Optional.of(locale("vi", true)));

        String resolved = resolver.resolve("vi", "en-US,en;q=0.8");

        assertThat(resolved).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolve - đọc Accept-Language dạng region và fallback về base language")
    void resolve_acceptLanguageRegion_returnsBaseLocale() {
        when(localeRepository.findByCodeAndEnabledTrue("vi-vn")).thenReturn(Optional.empty());
        when(localeRepository.findByCodeAndEnabledTrue("vi")).thenReturn(Optional.of(locale("vi", true)));

        String resolved = resolver.resolve(null, "vi-VN, en-US;q=0.8");

        assertThat(resolved).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolve - tôn trọng q-weight và bỏ qua q=0")
    void resolve_acceptLanguageQuality_prefersHighestPositiveWeight() {
        when(localeRepository.findByCodeAndEnabledTrue("vi")).thenReturn(Optional.of(locale("vi", true)));

        String resolved = resolver.resolve(null, "en;q=0, vi;q=1");

        assertThat(resolved).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolve - ưu tiên region có q cao rồi fallback base language")
    void resolve_weightedRegion_fallsBackToBaseBeforeLowerWeightRange() {
        when(localeRepository.findByCodeAndEnabledTrue("en-us")).thenReturn(Optional.empty());
        when(localeRepository.findByCodeAndEnabledTrue("en")).thenReturn(Optional.of(locale("en", false)));

        String resolved = resolver.resolve(null, "vi;q=0.5, en-US;q=0.9");

        assertThat(resolved).isEqualTo("en");
    }

    @Test
    @DisplayName("resolve - bỏ qua range malformed và q-value không hợp lệ")
    void resolve_malformedRanges_ignoresThemAndUsesValidCandidate() {
        when(localeRepository.findByCodeAndEnabledTrue("vi")).thenReturn(Optional.of(locale("vi", true)));

        String resolved = resolver.resolve(null, "not-a-language, en;q=oops, vi;q=0.7");

        assertThat(resolved).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolve - fallback về locale mặc định khi locale không hỗ trợ")
    void resolve_unsupportedLocale_returnsDefaultLocale() {
        CatalogLocale vi = locale("vi", true);
        when(localeRepository.findByCodeAndEnabledTrue("en")).thenReturn(Optional.empty());
        when(localeRepository.findFirstByDefaultLocaleTrueAndEnabledTrue()).thenReturn(Optional.of(vi));

        String resolved = resolver.resolve("en", null);

        assertThat(resolved).isEqualTo("vi");
    }

    private CatalogLocale locale(String code, boolean defaultLocale) {
        CatalogLocale locale = new CatalogLocale();
        locale.setCode(code);
        locale.setName(code);
        locale.setDefaultLocale(defaultLocale);
        locale.setEnabled(true);
        return locale;
    }
}
