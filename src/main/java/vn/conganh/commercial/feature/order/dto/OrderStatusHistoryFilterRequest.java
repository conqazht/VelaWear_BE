package vn.conganh.commercial.feature.order.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record OrderStatusHistoryFilterRequest(
        String fromStatus,
        String toStatus,
        Long changedBy,
        String reason,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
