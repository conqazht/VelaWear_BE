package vn.conganh.commercial.feature.notification.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import vn.conganh.commercial.util.constant.NotificationType;

public record NotificationFilterRequest(
        Boolean isRead,
        NotificationType type,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo
) {}
