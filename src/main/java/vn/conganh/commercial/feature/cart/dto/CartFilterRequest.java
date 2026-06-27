package vn.conganh.commercial.feature.cart.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record CartFilterRequest(
        Long userId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
