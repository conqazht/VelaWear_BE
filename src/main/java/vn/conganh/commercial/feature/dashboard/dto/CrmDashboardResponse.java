package vn.conganh.commercial.feature.dashboard.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrmDashboardResponse {
    private String period;
    private CrmKpiSummary kpis;
    private List<CustomerGrowthChartPoint> customerGrowthChart;
    private List<MembershipTierSummary> membershipTiers;
    private List<TopCustomerSummary> topCustomers;
}
