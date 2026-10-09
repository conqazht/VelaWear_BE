package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopProductSummary {
    private String productName;
    private String productSlug;
    private String image;
    private Long soldQuantity;
    private BigDecimal totalRevenue;
}
