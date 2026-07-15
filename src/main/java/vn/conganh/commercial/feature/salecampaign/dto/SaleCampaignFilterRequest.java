package vn.conganh.commercial.feature.salecampaign.dto;

import vn.conganh.commercial.feature.salecampaign.SaleCampaignPhase;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignStatus;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignType;

public record SaleCampaignFilterRequest(
        String search,
        SaleCampaignType type,
        SaleCampaignStatus status,
        SaleCampaignPhase phase
) {}
