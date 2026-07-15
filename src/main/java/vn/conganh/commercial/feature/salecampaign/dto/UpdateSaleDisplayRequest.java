package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSaleDisplayRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 500) String bannerUrl
) {}
