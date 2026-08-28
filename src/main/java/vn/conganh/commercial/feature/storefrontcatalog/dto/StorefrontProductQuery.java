package vn.conganh.commercial.feature.storefrontcatalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import vn.conganh.commercial.exception.InvalidRequestException;

public record StorefrontProductQuery(
        @Size(max = 120, message = "Search query must not exceed 120 characters")
        String q,

        @Valid
        List<@NotBlank(message = "Category slug must not be blank")
                @Size(max = 180, message = "Category slug must not exceed 180 characters") String> categorySlugs,

        @Valid
        List<@Positive(message = "Color id must be positive") Long> colorIds,

        @Valid
        List<@Positive(message = "Size id must be positive") Long> sizeIds,

        @DecimalMin(value = "0.00", message = "Minimum price must be zero or greater")
        BigDecimal minPrice,

        @DecimalMin(value = "0.00", message = "Maximum price must be zero or greater")
        BigDecimal maxPrice,

        @Pattern(
                regexp = "(?i)^(featured|newest|price-asc|price-desc)$",
                message = "Sort must be featured, newest, price-asc, or price-desc")
        String sort,

        @Min(value = 1, message = "Page must be at least 1")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 60, message = "Page size must not exceed 60")
        Integer size,

        @Size(max = 35, message = "Locale must not exceed 35 characters")
        String locale
) {

    public int resolvedPage() {
        return page == null ? 1 : page;
    }

    public int resolvedSize() {
        return size == null ? 12 : size;
    }

    public void assertPriceRangeValid() {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidRequestException(
                    "Minimum price must not be greater than maximum price");
        }
    }
}
