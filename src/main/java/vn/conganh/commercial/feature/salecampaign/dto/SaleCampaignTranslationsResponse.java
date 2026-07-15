package vn.conganh.commercial.feature.salecampaign.dto;

import java.util.List;

public record SaleCampaignTranslationsResponse(
        long version,
        List<SaleCampaignTranslationResponse> translations
) {}
