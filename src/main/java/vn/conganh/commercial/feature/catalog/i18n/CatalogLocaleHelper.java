package vn.conganh.commercial.feature.catalog.i18n;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import vn.conganh.commercial.exception.InvalidRequestException;

public final class CatalogLocaleHelper {

    private CatalogLocaleHelper() {}

    public static void validateBatchLocales(Collection<String> localeCodes) {
        Set<String> seen = new HashSet<>();
        for (String localeCode : localeCodes) {
            if (!seen.add(localeCode)) {
                throw new InvalidRequestException("Duplicate locale in translation batch: " + localeCode);
            }
        }
    }

    public static void assertNotDefaultLocale(String localeCode) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(localeCode)) {
            throw new InvalidRequestException("The default locale translation cannot be deleted");
        }
    }

    public static int compareLocales(String left, String right) {
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(left)) {
            return CatalogLocaleResolver.DEFAULT_LOCALE.equals(right) ? 0 : -1;
        }
        if (CatalogLocaleResolver.DEFAULT_LOCALE.equals(right)) {
            return 1;
        }
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    public static String firstValue(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
