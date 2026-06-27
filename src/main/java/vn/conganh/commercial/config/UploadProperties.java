package vn.conganh.commercial.config;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.upload")
public record UploadProperties(
        String baseDir,
        String urlPrefix,
        long maxSizeBytes,
        List<String> allowedExtensions,
        List<String> allowedFolders
) {

    public UploadProperties {
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalArgumentException("Upload base directory is required");
        }
        if (urlPrefix == null || urlPrefix.isBlank()) {
            throw new IllegalArgumentException("Upload URL prefix is required");
        }
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("Upload max size must be greater than zero");
        }

        urlPrefix = normalizeUrlPrefix(urlPrefix);
        allowedExtensions = normalizeValues(allowedExtensions);
        allowedFolders = normalizeValues(allowedFolders);

        if (allowedExtensions.isEmpty()) {
            throw new IllegalArgumentException("Allowed upload extensions are required");
        }
        if (allowedFolders.isEmpty()) {
            throw new IllegalArgumentException("Allowed upload folders are required");
        }
    }

    private static String normalizeUrlPrefix(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("/") || normalized.contains("://")) {
            return normalized;
        }
        return "/" + normalized;
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
