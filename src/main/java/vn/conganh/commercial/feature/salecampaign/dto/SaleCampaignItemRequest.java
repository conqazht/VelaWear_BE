package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SaleCampaignItemRequest(
        @NotNull Long variantId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal promotionalPrice,
        @Min(1) Integer quota,
        @Min(1) Integer maxPerCustomer
) {}
