package vn.conganh.commercial.feature.category.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateCategoryTranslationsRequest(
        @NotEmpty List<@Valid CategoryTranslationRequest> translations
) {}
