package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinanceKpiSummary {
    private BigDecimal netCollectedRevenue;
    private Double netCollectedChangePercentage;
    private BigDecimal pendingRevenue;
    private BigDecimal refundedRevenue;
    private BigDecimal totalDiscounts;
    private Long paidOrdersCount;
    private Long pendingOrdersCount;
}
