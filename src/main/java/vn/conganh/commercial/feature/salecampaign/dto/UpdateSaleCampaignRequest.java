package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignType;

public record UpdateSaleCampaignRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 500) String bannerUrl,
        @NotNull SaleCampaignType type,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotEmpty List<@Valid SaleCampaignItemRequest> items
) {}
