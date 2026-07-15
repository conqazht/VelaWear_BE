package vn.conganh.commercial.feature.salecampaign.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.conganh.commercial.feature.salecampaign.SaleCampaign;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignPhase;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignType;

public record SaleCampaignResponse(
        Long id,
        String code,
        String name,
        String description,
        String bannerUrl,
        SaleCampaignType type,
        SaleCampaignStatus status,
        SaleCampaignPhase phase,
        Instant startsAt,
        Instant endsAt,
        long version,
        List<SaleCampaignItemResponse> items,
        List<String> translationLocales,
        Instant createdAt,
        Instant updatedAt
) {
    public static SaleCampaignResponse fromEntity(SaleCampaign campaign, Instant now) {
        return fromEntity(campaign, now, Map.of());
    }

    public static SaleCampaignResponse fromEntity(SaleCampaign campaign, Instant now, Map<Long, String> imagesByVariant) {
        return fromEntity(
                campaign,
                now,
                imagesByVariant,
                Map.of(),
                Map.of(),
                campaign.getName(),
                campaign.getDescription(),
                List.of());
    }

    public static SaleCampaignResponse fromEntity(
            SaleCampaign campaign,
            Instant now,
            Map<Long, String> imagesByVariant,
            Map<Long, String> productNames,
            Map<Long, String> productSlugs,
            String localizedName,
            String localizedDescription,
            List<String> translationLocales) {
        return new SaleCampaignResponse(
                campaign.getId(),
                campaign.getCode(),
                localizedName,
                localizedDescription,
                campaign.getBannerUrl(),
                campaign.getType(),
                campaign.getStatus(),
                SaleCampaignPhase.from(campaign.getStatus(), campaign.getStartsAt(), campaign.getEndsAt(), now),
                campaign.getStartsAt(),
                campaign.getEndsAt(),
                campaign.getVersion(),
                campaign.getItems().stream()
                        .map(item -> SaleCampaignItemResponse.fromEntity(
                                item,
                                imagesByVariant.get(item.getVariant().getId()),
                                productNames.getOrDefault(
                                        item.getVariant().getProduct().getId(),
                                        item.getVariant().getProduct().getName()),
                                productSlugs.getOrDefault(
                                        item.getVariant().getProduct().getId(),
                                        item.getVariant().getProduct().getSlug())))
                        .toList(),
                translationLocales,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }
}
