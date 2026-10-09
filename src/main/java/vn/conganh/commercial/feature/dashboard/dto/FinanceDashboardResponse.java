package vn.conganh.commercial.feature.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FinanceDashboardResponse {
    private String period;
    private FinanceKpiSummary kpis;
    private List<CashflowChartPoint> cashflowChart;
    private List<PaymentMethodSummary> paymentMethods;
    private List<PaymentStatusSummary> paymentStatuses;
    private List<RecentPaymentTransaction> recentTransactions;
}
