package vn.conganh.commercial.feature.brand.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record BrandFilterRequest(
        String name,
        String slug,
        String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
