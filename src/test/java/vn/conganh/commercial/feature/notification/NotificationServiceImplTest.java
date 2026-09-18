package vn.conganh.commercial.feature.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.feature.notification.dto.NotificationResponse;
import vn.conganh.commercial.feature.notification.dto.UnreadCountResponse;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.NotificationTargetType;
import vn.conganh.commercial.util.constant.NotificationType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Notification - NotificationServiceImpl")
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationServiceImpl notificationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationRepository, userRepository);
        testUser = new User();
        ReflectionTestUtils.setField(testUser, "id", 1L);
        testUser.setEmail("customer@velawear.vn");
        testUser.setFullName("Nguyen Van A");
    }

    @Test
    @DisplayName("getMyNotifications: should return paginated notifications for current user")
    void getMyNotifications_success() {
        when(userRepository.findByEmailAndDeletedAtIsNull("customer@velawear.vn"))
                .thenReturn(Optional.of(testUser));

        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", 10L);
        notification.setUser(testUser);
        notification.setTitle("Test Title");
        notification.setContent("Test Content");
        notification.setType(NotificationType.ORDER_UPDATE);

        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(notification), pageable, 1));

        ResultPaginationDTO result = notificationService.getMyNotifications(
                "customer@velawear.vn", new NotificationFilterRequest(null, null, null, null), pageable);

        assertThat(result).isNotNull();
        assertThat(result.meta().total()).isEqualTo(1);
        assertThat(result.result()).hasSize(1);
    }

    @Test
    @DisplayName("getMyUnreadCount: should return unread notification count")
    void getMyUnreadCount_success() {
        when(userRepository.findByEmailAndDeletedAtIsNull("customer@velawear.vn"))
                .thenReturn(Optional.of(testUser));
        when(notificationRepository.countByUserIdAndIsReadFalse(1L)).thenReturn(3L);

        UnreadCountResponse response = notificationService.getMyUnreadCount("customer@velawear.vn");

        assertThat(response.unreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("markAsRead: should mark notification as read")
    void markAsRead_success() {
        when(userRepository.findByEmailAndDeletedAtIsNull("customer@velawear.vn"))
                .thenReturn(Optional.of(testUser));

        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", 10L);
        notification.setUser(testUser);
        notification.setTitle("Test");
        notification.setContent("Content");
        notification.setType(NotificationType.ORDER_UPDATE);
        notification.setRead(false);

        when(notificationRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead("customer@velawear.vn", 10L);

        assertThat(response.isRead()).isTrue();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    @DisplayName("markAllAsRead: should execute batch update query")
    void markAllAsRead_success() {
        when(userRepository.findByEmailAndDeletedAtIsNull("customer@velawear.vn"))
                .thenReturn(Optional.of(testUser));

        notificationService.markAllAsRead("customer@velawear.vn");

        verify(notificationRepository).markAllAsReadByUserId(eq(1L), any());
    }

    @Test
    @DisplayName("notifyOrderStatusChanged: should create notification for CONFIRMED status")
    void notifyOrderStatusChanged_confirmed() {
        Order order = new Order();
        order.setUser(testUser);
        order.setOrderCode("VELA-12345");

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyOrderStatusChanged(order, "PENDING", "CONFIRMED");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getTitle()).contains("VELA-12345");
        assertThat(saved.getTitle()).contains("xác nhận");
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_UPDATE);
        assertThat(saved.getTargetType()).isEqualTo(NotificationTargetType.ORDER);
        assertThat(saved.getTargetId()).isEqualTo("VELA-12345");
        assertThat(saved.getLinkUrl()).isEqualTo("/profile/orders/VELA-12345");
    }
}
