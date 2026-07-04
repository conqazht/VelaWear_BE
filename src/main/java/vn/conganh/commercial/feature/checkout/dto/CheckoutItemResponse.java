package vn.conganh.commercial.feature.checkout.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.order.OrderItem;

public record CheckoutItemResponse(
        long orderItemId,
        long variantId,
        String productName,
        String variantName,
        String sku,
        String image,
        BigDecimal price,
        int quantity,
        BigDecimal subtotal
) {

    public static CheckoutItemResponse fromEntity(OrderItem orderItem) {
        return new CheckoutItemResponse(
                orderItem.getId(),
                orderItem.getVariantId(),
                orderItem.getProductName(),
                orderItem.getVariantName(),
                orderItem.getSku(),
                orderItem.getImage(),
                orderItem.getPrice(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}
