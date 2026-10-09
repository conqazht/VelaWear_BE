package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EcommerceDashboardResponse {
    private String period;
    private DashboardKpiSummary kpis;
    private List<RevenueChartPoint> revenueChart;
    private List<RecentOrderSummary> recentOrders;
    private List<TopProductSummary> topProducts;
    private InventorySummary inventory;
    private CustomerReviewSummary customerReviews;
}
