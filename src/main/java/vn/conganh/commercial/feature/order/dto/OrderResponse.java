package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.user.User;

import vn.conganh.commercial.feature.checkout.dto.PaymentInitiationResponse;

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
        Instant paymentDueAt,
        Instant reservationExpiresAt,
        Instant resourcesReleasedAt,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        PaymentInitiationResponse paymentInitiation
) {

    public static OrderResponse fromEntity(Order order) {
        return fromEntity(order, List.of(), null);
    }

    public static OrderResponse fromEntity(Order order, List<OrderItem> items) {
        return fromEntity(order, items, null);
    }

    public static OrderResponse fromEntity(Order order, List<OrderItem> items, PaymentInitiationResponse paymentInitiation) {
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
                order.getPaymentDueAt(),
                order.getReservationExpiresAt(),
                order.getResourcesReleasedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items == null
                        ? List.of()
                        : items.stream().map(OrderItemResponse::fromEntity).toList(),
                paymentInitiation);
    }
}
