package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;

public record OrderResponse(
        Long id,
        Long userId,
        String userFullName,
        String userEmail,
        String orderCode,
        String status,
        BigDecimal subtotal,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String paymentMethod,
        String paymentStatus,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse fromEntity(Order order) {
        User user = order.getUser();
        return new OrderResponse(
                order.getId(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                order.getOrderCode(),
                order.getStatus(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
