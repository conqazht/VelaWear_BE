package vn.conganh.commercial.feature.notification.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.notification.Notification;
import vn.conganh.commercial.util.constant.NotificationTargetType;
import vn.conganh.commercial.util.constant.NotificationType;

public record NotificationResponse(
        Long id,
        String title,
        String content,
        NotificationType type,
        NotificationTargetType targetType,
        String targetId,
        String linkUrl,
        String imageUrl,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse fromEntity(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getType(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getLinkUrl(),
                notification.getImageUrl(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
