package vn.conganh.commercial.feature.permission.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record PermissionFilterRequest(
        String name,
        String apiPath,
        String method,
        String module,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedTo
) {}
