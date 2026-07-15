package vn.conganh.commercial.feature.catalog.i18n.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EnglishContentGenerationResult(Map<String, String> fields) {
    public EnglishContentGenerationResult {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
