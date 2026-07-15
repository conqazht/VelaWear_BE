package vn.conganh.commercial.feature.salecampaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EndAndCloneSaleCampaignRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {}
