package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.order.OrderItem;
import vn.conganh.commercial.feature.salecampaign.PriceSource;

public record OrderItemResponse(
        Long id,
        Long variantId,
        String productName,
        String productSlug,
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
        BigDecimal subtotal,
        String status,
        Instant createdAt
) {
    public static OrderItemResponse fromEntity(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getVariantId(),
                item.getProductName(),
                item.getProductSlug(),
                item.getVariantName(),
                item.getSku(),
                item.getImage(),
                item.getListPrice(),
                item.getPrice(),
                item.getPriceSource(),
                item.getSaleCampaignItem() == null ? null : item.getSaleCampaignItem().getId(),
                item.getSaleCampaignCode(),
                item.getSaleCampaignName(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getStatus(),
                item.getCreatedAt());
    }
}
