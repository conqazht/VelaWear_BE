package vn.conganh.commercial.feature.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        UUID categoryId,

        @NotBlank(message = "Sku is required")
        @Size(max = 80, message = "Sku must be at most 80 characters")
        String sku,

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @NotBlank(message = "Slug is required")
        @Size(max = 280, message = "Slug must be at most 280 characters")
        String slug,

        String description,

        @NotBlank(message = "Status is required")
        @Size(max = 30, message = "Status must be at most 30 characters")
        String status,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.00", message = "Base price must be greater than or equal to 0")
        BigDecimal basePrice,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be 3 characters")
        String currency
) {
}
