package vn.conganh.commercial.feature.notification;

import org.springframework.data.domain.Pageable;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.feature.notification.dto.NotificationResponse;
import vn.conganh.commercial.feature.notification.dto.UnreadCountResponse;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.util.constant.NotificationTargetType;
import vn.conganh.commercial.util.constant.NotificationType;

public interface NotificationService {

    ResultPaginationDTO getMyNotifications(String email, NotificationFilterRequest filter, Pageable pageable);

    UnreadCountResponse getMyUnreadCount(String email);

    NotificationResponse markAsRead(String email, Long notificationId);

    void markAllAsRead(String email);

    Notification createNotification(
            User user,
            String title,
            String content,
            NotificationType type,
            NotificationTargetType targetType,
            String targetId,
            String linkUrl,
            String imageUrl
    );

    void notifyOrderStatusChanged(Order order, String previousStatus, String newStatus);
}
