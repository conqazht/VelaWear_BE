package vn.conganh.commercial.feature.productvariant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.productvariant.ProductVariant;
import vn.conganh.commercial.feature.salecampaign.VariantPricing;
import vn.conganh.commercial.feature.salecampaign.dto.VariantPricingResponse;

public record ProductVariantResponse(
        Long id,
        ProductInfo product,
        String sku,
        BigDecimal price,
        VariantPricingResponse pricing,
        Integer stockQuantity,
        ColorInfo color,
        SizeInfo size,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductVariantResponse fromEntity(ProductVariant variant) {
        return fromEntity(variant, null);
    }

    public static ProductVariantResponse fromEntity(ProductVariant variant, VariantPricing pricing) {
        ProductInfo productInfo = null;
        if (variant.getProduct() != null) {
            productInfo = new ProductInfo(variant.getProduct().getId(), variant.getProduct().getName());
        }
        ColorInfo colorInfo = null;
        if (variant.getColor() != null) {
            colorInfo = new ColorInfo(variant.getColor().getId(), variant.getColor().getName());
        }
        SizeInfo sizeInfo = null;
        if (variant.getSize() != null) {
            sizeInfo = new SizeInfo(variant.getSize().getId(), variant.getSize().getName());
        }
        return new ProductVariantResponse(
                variant.getId(),
                productInfo,
                variant.getSku(),
                variant.getPrice(),
                pricing == null ? null : pricing.toResponse(),
                variant.getStockQuantity(),
                colorInfo,
                sizeInfo,
                variant.getStatus(),
                variant.getCreatedAt(),
                variant.getUpdatedAt());
    }

    public record ProductInfo(Long id, String name) {}

    public record ColorInfo(Long id, String name) {}

    public record SizeInfo(Long id, String name) {}
}
