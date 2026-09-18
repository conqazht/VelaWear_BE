package vn.conganh.commercial.feature.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.dto.ResultPaginationDTO;
import vn.conganh.commercial.feature.notification.dto.NotificationFilterRequest;
import vn.conganh.commercial.feature.notification.dto.NotificationResponse;
import vn.conganh.commercial.feature.notification.dto.UnreadCountResponse;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Customer notification management and unread count")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Lấy danh sách thông báo của tôi (có phân trang & lọc isRead)")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ResultPaginationDTO>> getMyNotifications(
            @AuthenticationPrincipal Jwt jwt,
            @ParameterObject NotificationFilterRequest filter,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyNotifications(jwt.getSubject(), filter, pageable)));
    }

    @Operation(summary = "Lấy số lượng thông báo chưa đọc (badge chuông đỏ)")
    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getMyUnreadCount(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getMyUnreadCount(jwt.getSubject())));
    }

    @Operation(summary = "Đánh dấu 1 thông báo là đã đọc")
    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(jwt.getSubject(), id)));
    }

    @Operation(summary = "Đánh dấu tất cả thông báo là đã đọc")
    @PutMapping("/me/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllAsRead(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
