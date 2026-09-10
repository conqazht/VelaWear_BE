package vn.conganh.commercial.feature.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.feature.notification.dto.NotificationResponse;
import vn.conganh.commercial.feature.notification.dto.UnreadCountResponse;
import vn.conganh.commercial.util.constant.NotificationTargetType;
import vn.conganh.commercial.util.constant.NotificationType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module Notification - NotificationController (Unit)")
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private static final String TEST_EMAIL = "customer@velawear.vn";

    @BeforeEach
    void setUp() {
        // Mock resolver for @AuthenticationPrincipal Jwt jwt
        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(Jwt.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return Jwt.withTokenValue("mock-token")
                        .header("alg", "HS512")
                        .subject(TEST_EMAIL)
                        .claim("email", TEST_EMAIL)
                        .build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(jwtResolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/notifications/me - returns paginated notifications")
    void getMyNotifications_returns200() throws Exception {
        NotificationResponse item = new NotificationResponse(
                1L, "Đơn hàng mới", "Nội dung", NotificationType.ORDER_UPDATE,
                NotificationTargetType.ORDER, "VELA-001", "/profile/orders/VELA-001",
                null, false, null, Instant.now()
        );
        ResultPaginationDTO pagination = ResultPaginationDTO.fromPage(
                new PageImpl<>(List.of(item))
        );

        when(notificationService.getMyNotifications(eq(TEST_EMAIL), any(), any()))
                .thenReturn(pagination);

        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].title").value("Đơn hàng mới"));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/me/unread-count - returns unread count")
    void getMyUnreadCount_returns200() throws Exception {
        when(notificationService.getMyUnreadCount(TEST_EMAIL))
                .thenReturn(new UnreadCountResponse(5L));

        mockMvc.perform(get("/api/v1/notifications/me/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/{id}/read - marks single notification as read")
    void markAsRead_returns200() throws Exception {
        NotificationResponse item = new NotificationResponse(
                10L, "Title", "Content", NotificationType.ORDER_UPDATE,
                null, null, null, null, true, Instant.now(), Instant.now()
        );
        when(notificationService.markAsRead(TEST_EMAIL, 10L)).thenReturn(item);

        mockMvc.perform(put("/api/v1/notifications/10/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/notifications/me/read-all - marks all as read")
    void markAllAsRead_returns200() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/me/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(notificationService).markAllAsRead(TEST_EMAIL);
    }
}
