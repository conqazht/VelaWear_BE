package vn.conganh.commercial.feature.role.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record RoleFilterRequest(
        String name,
        String description,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedTo
) {}
