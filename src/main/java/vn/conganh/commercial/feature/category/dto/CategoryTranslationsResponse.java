package vn.conganh.commercial.feature.category.dto;

import java.util.List;

public record CategoryTranslationsResponse(
        List<CategoryTranslationResponse> translations
) {}
