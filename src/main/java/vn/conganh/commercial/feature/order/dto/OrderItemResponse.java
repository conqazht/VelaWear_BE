package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.order.OrderItem;

public record OrderItemResponse(
        Long id,
        Long variantId,
        String productName,
        String variantName,
        String sku,
        String image,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal,
        String status,
        Instant createdAt
) {
    public static OrderItemResponse fromEntity(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getVariantId(),
                item.getProductName(),
                item.getVariantName(),
                item.getSku(),
                item.getImage(),
                item.getPrice(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getStatus(),
                item.getCreatedAt());
    }
}
