package vn.conganh.commercial.feature.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateProductTranslationsRequest(
        @NotEmpty List<@Valid ProductTranslationRequest> translations
) {}
