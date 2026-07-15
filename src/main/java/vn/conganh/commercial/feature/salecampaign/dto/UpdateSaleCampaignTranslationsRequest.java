package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateSaleCampaignTranslationsRequest(
        @NotNull Long version,
        @NotEmpty List<@Valid SaleCampaignTranslationRequest> translations
) {}
