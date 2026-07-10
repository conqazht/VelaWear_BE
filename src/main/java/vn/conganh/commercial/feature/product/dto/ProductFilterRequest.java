package vn.conganh.commercial.feature.product.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ProductFilterRequest(
        Long categoryId,
        Long brandId,
        String name,
        String slug,
        String status,
        Boolean global,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        Long colorId,
        Long sizeId,
        java.math.BigDecimal minPrice,
        java.math.BigDecimal maxPrice
) {}
