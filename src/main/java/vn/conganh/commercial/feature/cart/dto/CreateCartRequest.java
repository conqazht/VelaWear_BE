package vn.conganh.commercial.feature.cart.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCartRequest(

        @NotNull(message = "User ID is required")
        Long userId
) {
}
