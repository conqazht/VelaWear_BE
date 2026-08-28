package vn.conganh.commercial.feature.catalog.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.conganh.commercial.exception.InvalidRequestException;

@DisplayName("Catalog - CatalogLocaleHelper")
class CatalogLocaleHelperTest {

    @Nested
    @DisplayName("validateBatchLocales")
    class ValidateBatchLocales {

        @Test
        @DisplayName("valid batch without duplicates passes")
        void validBatch_passes() {
            assertThatCode(() -> CatalogLocaleHelper.validateBatchLocales(List.of("vi", "en", "ja")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("batch with duplicate locale throws InvalidRequestException")
        void duplicateLocale_throwsException() {
            assertThatThrownBy(() -> CatalogLocaleHelper.validateBatchLocales(List.of("vi", "en", "vi")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Duplicate locale in translation batch: vi");
        }
    }

    @Nested
    @DisplayName("assertNotDefaultLocale")
    class AssertNotDefaultLocale {

        @Test
        @DisplayName("default locale throws InvalidRequestException")
        void defaultLocale_throwsException() {
            assertThatThrownBy(() -> CatalogLocaleHelper.assertNotDefaultLocale("vi"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("The default locale translation cannot be deleted");
        }

        @Test
        @DisplayName("non-default locale passes")
        void nonDefaultLocale_passes() {
            assertThatCode(() -> CatalogLocaleHelper.assertNotDefaultLocale("en"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("compareLocales")
    class CompareLocales {

        @Test
        @DisplayName("default locale 'vi' is always prioritized first")
        void defaultLocale_prioritizedFirst() {
            assertThat(CatalogLocaleHelper.compareLocales("vi", "en")).isNegative();
            assertThat(CatalogLocaleHelper.compareLocales("en", "vi")).isPositive();
            assertThat(CatalogLocaleHelper.compareLocales("vi", "vi")).isZero();
        }

        @Test
        @DisplayName("non-default locales are sorted alphabetically")
        void nonDefaultLocales_sortedAlphabetically() {
            assertThat(CatalogLocaleHelper.compareLocales("en", "ja")).isNegative();
            assertThat(CatalogLocaleHelper.compareLocales("ja", "en")).isPositive();
            assertThat(CatalogLocaleHelper.compareLocales("en", "en")).isZero();
        }

        @Test
        @DisplayName("null values handled safely")
        void nullValues_handledSafely() {
            assertThat(CatalogLocaleHelper.compareLocales(null, "en")).isPositive();
            assertThat(CatalogLocaleHelper.compareLocales("en", null)).isNegative();
        }
    }

    @Nested
    @DisplayName("firstValue")
    class FirstValue {

        @Test
        @DisplayName("returns first non-null and non-blank string")
        void returnsFirstNonNullNonBlank() {
            assertThat(CatalogLocaleHelper.firstValue(null, " ", "", "actual value", "fallback"))
                    .isEqualTo("actual value");
        }

        @Test
        @DisplayName("returns null when all candidates are null or blank")
        void allBlank_returnsNull() {
            assertThat(CatalogLocaleHelper.firstValue(null, " ", "\t")).isNull();
            assertThat(CatalogLocaleHelper.firstValue((String[]) null)).isNull();
        }
    }
}
