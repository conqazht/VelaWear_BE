package vn.conganh.commercial.feature.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateReviewRequest(

    @NotNull(message = "User ID is required")
    Long userId,

    @NotNull(message = "Order item ID is required")
    Long orderItemId,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Short rating,

        String comment
) {
}
