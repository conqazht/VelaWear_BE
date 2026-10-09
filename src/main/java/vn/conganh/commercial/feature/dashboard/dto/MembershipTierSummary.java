package vn.conganh.commercial.feature.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MembershipTierSummary {
    private String tier;
    private String label;
    private Long count;
    private Double percentage;
}
