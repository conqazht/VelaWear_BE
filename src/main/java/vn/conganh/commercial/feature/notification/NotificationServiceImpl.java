package vn.conganh.commercial.feature.notification;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.exception.ResourceNotFoundException;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.feature.notification.dto.NotificationResponse;
import vn.conganh.commercial.feature.notification.dto.UnreadCountResponse;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.NotificationTargetType;
import vn.conganh.commercial.util.constant.NotificationType;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyNotifications(String email, NotificationFilterRequest filter, Pageable pageable) {
        User user = findActiveUser(email);
        Page<NotificationResponse> page = notificationRepository
                .findAll(Specification.where(NotificationSpecification.build(user.getId(), filter)), pageable)
                .map(NotificationResponse::fromEntity);
        return ResultPaginationDTO.fromPage(page);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getMyUnreadCount(String email) {
        User user = findActiveUser(email);
        long count = notificationRepository.countByUserIdAndIsReadFalse(user.getId());
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(String email, Long notificationId) {
        User user = findActiveUser(email);
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        notification.markAsRead();
        return NotificationResponse.fromEntity(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void markAllAsRead(String email) {
        User user = findActiveUser(email);
        notificationRepository.markAllAsReadByUserId(user.getId(), Instant.now());
    }

    @Override
    @Transactional
    public Notification createNotification(
            User user,
            String title,
            String content,
            NotificationType type,
            NotificationTargetType targetType,
            String targetId,
            String linkUrl,
            String imageUrl) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setLinkUrl(linkUrl);
        notification.setImageUrl(imageUrl);

        return notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void notifyOrderStatusChanged(Order order, String previousStatus, String newStatus) {
        if (order == null || order.getUser() == null || newStatus == null || newStatus.equals(previousStatus)) {
            return;
        }

        String orderCode = order.getOrderCode();
        String title;
        String content;

        switch (newStatus) {
            case "CONFIRMED" -> {
                title = "Đơn hàng #" + orderCode + " đã được xác nhận";
                content = "Người bán đang chuẩn bị đóng gói kiện hàng của bạn.";
            }
            case "SHIPPING" -> {
                title = "Đơn hàng #" + orderCode + " đang trên đường giao";
                content = "Kiện hàng đã được bàn giao cho đơn vị vận chuyển.";
            }
            case "COMPLETED" -> {
                title = "Đơn hàng #" + orderCode + " đã giao thành công";
                content = "Cảm ơn bạn đã mua sắm tại Vela Wear! Hãy để lại đánh giá cho sản phẩm nhé.";
            }
            case "CANCELLED" -> {
                title = "Đơn hàng #" + orderCode + " đã bị hủy";
                content = "Đơn hàng của bạn đã được hủy thành công.";
            }
            default -> {
                title = "Cập nhật đơn hàng #" + orderCode;
                content = "Trạng thái đơn hàng đã chuyển sang: " + newStatus;
            }
        }

        String linkUrl = "/profile/orders/" + orderCode;

        createNotification(
                order.getUser(),
                title,
                content,
                NotificationType.ORDER_UPDATE,
                NotificationTargetType.ORDER,
                orderCode,
                linkUrl,
                null
        );
        log.info("Sent order status notification for user {} on order {}", order.getUser().getId(), orderCode);
    }

    private User findActiveUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
