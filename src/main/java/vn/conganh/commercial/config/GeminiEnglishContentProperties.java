package vn.conganh.commercial.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.english-content.gemini")
public record GeminiEnglishContentProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String defaultModel,
        Duration connectTimeout,
        Duration readTimeout,
        int maxInputCharacters,
        int maxOutputTokens
) {

    public static final String FALLBACK_MODEL = "gemini-3.1-flash-lite";
    public static final Set<String> SUPPORTED_MODELS = Set.of(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview");

    public GeminiEnglishContentProperties {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = normalizeBaseUrl(baseUrl);
        defaultModel = defaultModel == null || defaultModel.isBlank()
                ? FALLBACK_MODEL
                : defaultModel.trim();
        connectTimeout = positive(connectTimeout, Duration.ofSeconds(3));
        readTimeout = positive(readTimeout, Duration.ofSeconds(30));
        maxInputCharacters = maxInputCharacters > 0 ? maxInputCharacters : 30_000;
        maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 16_384;
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public boolean supports(String model) {
        return model != null && SUPPORTED_MODELS.contains(model);
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta"
                : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
