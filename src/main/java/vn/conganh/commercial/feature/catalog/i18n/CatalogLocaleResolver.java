package vn.conganh.commercial.feature.catalog.i18n;

import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogLocaleResolver {

    public static final String DEFAULT_LOCALE = "vi";

    private static final Pattern LANGUAGE_REGION = Pattern.compile("^[a-z]{2}(-[a-z]{2})?$");

    private final CatalogLocaleRepository localeRepository;

    public String resolve(String requestedLocale, String acceptLanguage) {
        Optional<String> explicitLocale = resolveCandidate(requestedLocale);
        if (explicitLocale.isPresent()) {
            return explicitLocale.get();
        }

        Optional<String> headerLocale = resolveAcceptLanguage(acceptLanguage);
        return headerLocale.orElseGet(this::defaultLocale);
    }

    private Optional<String> resolveAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Optional.empty();
        }

        for (String languageRange : acceptLanguage.split(",")) {
            String candidate = languageRange.split(";", 2)[0];
            Optional<String> resolved = resolveCandidate(candidate);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveCandidate(String candidate) {
        String normalized = normalize(candidate);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<String> exact = findEnabledCode(normalized);
        if (exact.isPresent()) {
            return exact;
        }

        int regionSeparator = normalized.indexOf('-');
        if (regionSeparator > 0) {
            return findEnabledCode(normalized.substring(0, regionSeparator));
        }
        return Optional.empty();
    }

    private Optional<String> findEnabledCode(String code) {
        return localeRepository.findByCodeAndEnabledTrue(code).map(CatalogLocale::getCode);
    }

    private String defaultLocale() {
        return localeRepository.findFirstByDefaultLocaleTrueAndEnabledTrue()
                .map(CatalogLocale::getCode)
                .orElse(DEFAULT_LOCALE);
    }

    private String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String normalized = candidate.trim().replace('_', '-').toLowerCase();
        if (!LANGUAGE_REGION.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }
}
