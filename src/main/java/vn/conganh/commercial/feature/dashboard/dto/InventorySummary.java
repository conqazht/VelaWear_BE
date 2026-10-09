package vn.conganh.commercial.feature.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventorySummary {
    private Long totalVariants;
    private Long inStockCount;
    private Long lowStockCount;
    private Long outOfStockCount;
}
