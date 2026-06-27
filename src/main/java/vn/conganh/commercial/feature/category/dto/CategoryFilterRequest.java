package vn.conganh.commercial.feature.category.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record CategoryFilterRequest(
        Long parentId,
        String name,
        String slug,
        String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
