package vn.conganh.commercial.feature.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long variantId,
        Long productId,
        String productSlug,
        String productName,
        String image,
        String sku,
        String color,
        String size,
        BigDecimal price,
        int quantity
) {}
