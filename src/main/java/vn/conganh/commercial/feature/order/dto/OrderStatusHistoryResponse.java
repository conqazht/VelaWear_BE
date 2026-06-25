package vn.conganh.commercial.feature.order.dto;

import java.time.Instant;
import vn.conganh.commercial.feature.order.OrderStatusHistory;

public record OrderStatusHistoryResponse(
        Long id,
        Long orderId,
        String fromStatus,
        String toStatus,
        Long changedBy,
        String reason,
        Instant createdAt
) {

    public static OrderStatusHistoryResponse fromEntity(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(
                history.getId(),
                history.getOrder().getId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedBy(),
                history.getReason(),
                history.getCreatedAt());
    }
}
