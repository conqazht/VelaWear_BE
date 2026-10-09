package vn.conganh.commercial.feature.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.dashboard.dto.CrmDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.EcommerceDashboardResponse;
import vn.conganh.commercial.feature.dashboard.dto.FinanceDashboardResponse;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Admin overview dashboard and business statistics endpoints")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/ecommerce")
    @Operation(summary = "Get ecommerce overview dashboard metrics and chart")
    public ResponseEntity<ApiResponse<EcommerceDashboardResponse>> getEcommerceDashboard(
            @RequestParam(required = false, defaultValue = "this-month") String period) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getEcommerceDashboard(period)));
    }

    @GetMapping("/finance")
    @Operation(summary = "Get finance and cashflow analytics dashboard")
    public ResponseEntity<ApiResponse<FinanceDashboardResponse>> getFinanceDashboard(
            @RequestParam(required = false, defaultValue = "this-month") String period) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getFinanceDashboard(period)));
    }

    @GetMapping("/crm")
    @Operation(summary = "Get customer relations and CRM analytics dashboard")
    public ResponseEntity<ApiResponse<CrmDashboardResponse>> getCrmDashboard(
            @RequestParam(required = false, defaultValue = "this-month") String period) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getCrmDashboard(period)));
    }
}
