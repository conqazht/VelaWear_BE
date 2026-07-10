package vn.conganh.commercial.feature.checkout.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CheckoutItemRequest(
        @NotNull(message = "Variant ID is required")
        Long variantId,

        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {}
