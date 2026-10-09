package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;

public interface TopSellingProductProjection {
    String getProductName();
    String getProductSlug();
    String getImage();
    Long getSoldQuantity();
    BigDecimal getTotalRevenue();
}
