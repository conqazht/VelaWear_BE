package vn.conganh.commercial.feature.checkout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.conganh.commercial.feature.order.Order;

public record CheckoutResponse(
        long orderId,
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
        List<CheckoutItemResponse> items,
        Long paymentId,
        Instant createdAt
) {

    public static CheckoutResponse fromEntity(Order order,
                                              List<CheckoutItemResponse> items,
                                              Long paymentId) {
        return new CheckoutResponse(
                order.getId(),
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
                items,
                paymentId,
                order.getCreatedAt()
        );
    }
}
