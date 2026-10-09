package vn.conganh.commercial.feature.dashboard;

import vn.conganh.commercial.feature.dashboard.dto.CrmDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.EcommerceDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.FinanceDashboardResponse;

public interface DashboardService {

    EcommerceDashboardResponse getEcommerceDashboard(String period);

    FinanceDashboardResponse getFinanceDashboard(String period);

    CrmDashboardResponse getCrmDashboard(String period);
}
