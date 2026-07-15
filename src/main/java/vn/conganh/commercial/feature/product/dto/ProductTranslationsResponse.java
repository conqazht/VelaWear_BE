package vn.conganh.commercial.feature.product.dto;

import java.util.List;

public record ProductTranslationsResponse(
        List<ProductTranslationResponse> translations
) {}
