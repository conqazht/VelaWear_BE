package vn.conganh.commercial.feature.salecampaign.dto;

import java.math.BigDecimal;
import vn.conganh.commercial.feature.salecampaign.SaleCampaignItem;

public record SaleCampaignItemResponse(
        Long id,
        Long variantId,
        Long productId,
        String productName,
        String productSlug,
        String image,
        String sku,
        String color,
        String size,
        BigDecimal referencePrice,
        BigDecimal promotionalPrice,
        Integer quota,
        int reservedQuantity,
        int soldQuantity,
        Integer remainingQuota,
        Integer maxPerCustomer,
        int stockQuantity,
        int availableQuantity
) {
    public static SaleCampaignItemResponse fromEntity(SaleCampaignItem item) {
        return fromEntity(item, null);
    }

    public static SaleCampaignItemResponse fromEntity(SaleCampaignItem item, String image) {
        var variant = item.getVariant();
        var product = variant == null ? null : variant.getProduct();
        return fromEntity(
                item,
                image,
                product == null ? null : product.getName(),
                product == null ? null : product.getSlug());
    }

    public static SaleCampaignItemResponse fromEntity(
            SaleCampaignItem item,
            String image,
            String productName,
            String productSlug) {
        var variant = item.getVariant();
        var product = variant == null ? null : variant.getProduct();
        return new SaleCampaignItemResponse(
                item.getId(),
                variant == null ? null : variant.getId(),
                product == null ? null : product.getId(),
                product == null ? null : productName,
                product == null ? null : productSlug,
                image,
                variant == null ? null : variant.getSku(),
                variant == null || variant.getColor() == null ? null : variant.getColor().getName(),
                variant == null || variant.getSize() == null ? null : variant.getSize().getName(),
                item.getReferencePrice(),
                item.getPromotionalPrice(),
                item.getQuota(),
                item.getReservedQuantity(),
                item.getSoldQuantity(),
                item.getQuota() == null ? null : item.remainingQuota(),
                item.getMaxPerCustomer(),
                Math.max(0, variant == null ? 0 : variant.getStockQuantity()),
                Math.min(
                        Math.max(0, variant == null ? 0 : variant.getStockQuantity()),
                        item.getQuota() == null ? Integer.MAX_VALUE : item.remainingQuota()));
    }
}
