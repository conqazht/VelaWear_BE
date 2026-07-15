package vn.conganh.commercial.feature.catalog.i18n;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.InvalidRequestException;

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

    public String requireEnabledLocale(String requestedLocale) {
        String normalized = normalize(requestedLocale);
        if (normalized == null) {
            throw new InvalidRequestException("Locale code is invalid");
        }
        return findEnabledCode(normalized)
                .orElseThrow(() -> new InvalidRequestException("Locale is not enabled: " + normalized));
    }

    private Optional<String> resolveAcceptLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Optional.empty();
        }

        List<WeightedLanguageRange> ranges = parseLanguageRanges(acceptLanguage);
        for (WeightedLanguageRange range : ranges) {
            Optional<String> resolved = resolveCandidate(range.candidate());
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private List<WeightedLanguageRange> parseLanguageRanges(String acceptLanguage) {
        List<WeightedLanguageRange> ranges = new ArrayList<>();
        String[] rawRanges = acceptLanguage.split(",");
        for (int index = 0; index < rawRanges.length; index++) {
            String[] parts = rawRanges[index].trim().split(";");
            if (parts.length == 0 || normalize(parts[0]) == null) {
                continue;
            }
            double quality = 1.0d;
            boolean valid = true;
            boolean qualitySeen = false;
            for (int parameterIndex = 1; parameterIndex < parts.length; parameterIndex++) {
                String parameter = parts[parameterIndex].trim();
                if (!parameter.regionMatches(true, 0, "q=", 0, 2)) {
                    continue;
                }
                if (qualitySeen) {
                    valid = false;
                    break;
                }
                qualitySeen = true;
                try {
                    quality = Double.parseDouble(parameter.substring(2).trim());
                } catch (NumberFormatException exception) {
                    valid = false;
                    break;
                }
                if (!Double.isFinite(quality) || quality < 0.0d || quality > 1.0d) {
                    valid = false;
                    break;
                }
            }
            if (valid && quality > 0.0d) {
                ranges.add(new WeightedLanguageRange(parts[0], quality, index));
            }
        }
        return ranges.stream()
                .sorted(Comparator.comparingDouble(WeightedLanguageRange::quality)
                        .reversed()
                        .thenComparingInt(WeightedLanguageRange::order))
                .toList();
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

    private record WeightedLanguageRange(String candidate, double quality, int order) {}
}
