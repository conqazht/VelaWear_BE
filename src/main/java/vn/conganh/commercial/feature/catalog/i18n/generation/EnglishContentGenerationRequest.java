package vn.conganh.commercial.feature.catalog.i18n.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record EnglishContentGenerationRequest(
        EnglishContentType contentType,
        String model,
        Map<String, String> sourceFields
) {
    public EnglishContentGenerationRequest {
        sourceFields = Collections.unmodifiableMap(new LinkedHashMap<>(sourceFields));
    }
}
