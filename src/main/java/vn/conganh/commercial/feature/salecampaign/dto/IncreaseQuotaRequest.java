package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IncreaseQuotaRequest(
        @NotNull Long version,
        @Min(1) int additionalQuantity
) {}
