package vn.conganh.commercial.feature.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceCartItemsRequest(
        @NotNull List<@Valid Item> items
) {
    public record Item(
            @NotNull Long variantId,
            @Min(1) int quantity
    ) {}
}
