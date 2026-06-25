package vn.conganh.commercial.feature.productvariant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductVariantRequest(

        @NotNull(message = "Product id is required")
        Long productId,

        @NotBlank(message = "Sku is required")
        @Size(max = 100, message = "Sku must be at most 100 characters")
        String sku,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "Sale price must be greater than or equal to 0")
        BigDecimal salePrice,

        Integer stockQuantity,

        Long colorId,

        Long sizeId,

        @NotBlank(message = "Status is required")
        @Size(max = 20, message = "Status must be at most 20 characters")
        String status
) {
}
