package vn.conganh.commercial.feature.productvariant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record ProductVariantFilterRequest(
        Long productId,
        Long colorId,
        Long sizeId,
        String sku,
        String status,
        BigDecimal priceFrom,
        BigDecimal priceTo,
        Integer stockFrom,
        Integer stockTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
