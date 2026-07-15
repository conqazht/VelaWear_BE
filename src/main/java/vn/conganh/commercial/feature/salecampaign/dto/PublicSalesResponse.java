package vn.conganh.commercial.feature.salecampaign.dto;

import java.time.Instant;
import java.util.List;

public record PublicSalesResponse(
        Instant serverTime,
        List<SaleCampaignResponse> campaigns
) {}
