package vn.conganh.commercial.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2ExchangeRequest(
        @NotBlank String code
) {
}
