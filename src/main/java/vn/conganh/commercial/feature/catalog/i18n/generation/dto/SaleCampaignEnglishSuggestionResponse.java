package vn.conganh.commercial.feature.catalog.i18n.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaleCampaignEnglishSuggestionResponse(
        @NotBlank String localeCode,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20_000) String description
) {}
