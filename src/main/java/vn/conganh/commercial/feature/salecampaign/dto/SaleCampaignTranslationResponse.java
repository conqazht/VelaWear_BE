package vn.conganh.commercial.feature.salecampaign.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignTranslation;

public record SaleCampaignTranslationResponse(
        String localeCode,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static SaleCampaignTranslationResponse fromEntity(SaleCampaignTranslation translation) {
        return new SaleCampaignTranslationResponse(
                translation.getLocaleCode(),
                translation.getName(),
                translation.getDescription(),
                translation.getCreatedAt(),
                translation.getUpdatedAt());
    }
}
