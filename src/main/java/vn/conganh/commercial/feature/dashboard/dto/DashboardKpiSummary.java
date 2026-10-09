package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardKpiSummary {
    private BigDecimal grossSales;
    private Double grossSalesChangePercentage;

    private Long totalOrders;
    private Double totalOrdersChangePercentage;

    private BigDecimal averageOrderValue;
    private Double aovChangePercentage;

    private Long cancellationsCount;
    private Double cancellationRate;
}
