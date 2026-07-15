package vn.conganh.commercial.feature.checkout.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.salecampaign.PriceSource;

public record CheckoutItemResponse(
        long orderItemId,
        long variantId,
        String productName,
        String variantName,
        String sku,
        String image,
        BigDecimal listPrice,
        BigDecimal price,
        PriceSource priceSource,
        Long saleCampaignItemId,
        String saleCampaignCode,
        String saleCampaignName,
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
                orderItem.getListPrice(),
                orderItem.getPrice(),
                orderItem.getPriceSource(),
                orderItem.getSaleCampaignItem() == null ? null : orderItem.getSaleCampaignItem().getId(),
                orderItem.getSaleCampaignCode(),
                orderItem.getSaleCampaignName(),
                orderItem.getQuantity(),
                orderItem.getSubtotal()
        );
    }
}
