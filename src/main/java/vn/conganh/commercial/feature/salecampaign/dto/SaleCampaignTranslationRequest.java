package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaleCampaignTranslationRequest(
        @NotBlank @Size(max = 10) String localeCode,
        @NotBlank @Size(max = 255) String name,
        String description
) {}
