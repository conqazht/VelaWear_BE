package vn.conganh.commercial.feature.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AuthenticatedIntegrationTest;
import vn.conganh.commercial.TestDataFactory;

@Transactional
@DisplayName("Module Dashboard - AdminDashboardController")
class AdminDashboardControllerTest extends AuthenticatedIntegrationTest {

    private static final String BASE_PATH = "/api/v1/admin/dashboard/ecommerce";

    @Autowired
    private TestDataFactory testDataFactory;

    private String adminToken;
    private String forbiddenToken;

    @BeforeEach
    void setUp() {
        testDataFactory.seedPermissions("DASHBOARD", "/api/v1/admin/dashboard/**", "GET");
        adminToken = testDataFactory.jwtWithPermission();
        forbiddenToken = testDataFactory.jwtWithoutPermission();
    }

    @AfterEach
    void tearDown() {
        testDataFactory.cleanup();
    }

    @Override
    protected String adminToken() {
        return adminToken;
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/ecommerce - Returns 200 with dashboard metrics")
    void getEcommerceDashboard_success() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "this-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.period").value("this-month"))
                .andExpect(jsonPath("$.data.kpis").exists())
                .andExpect(jsonPath("$.data.kpis.grossSales").isNumber())
                .andExpect(jsonPath("$.data.kpis.totalOrders").isNumber())
                .andExpect(jsonPath("$.data.revenueChart").isArray())
                .andExpect(jsonPath("$.data.recentOrders").isArray())
                .andExpect(jsonPath("$.data.topProducts").isArray())
                .andExpect(jsonPath("$.data.inventory").exists())
                .andExpect(jsonPath("$.data.customerReviews").exists());
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/ecommerce - Returns 403 when user lacks role/permission")
    void getEcommerceDashboard_forbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .header("Authorization", "Bearer " + forbiddenToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/finance - Returns 200 with finance metrics and cashflow")
    void getFinanceDashboard_success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/finance")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "this-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.period").value("this-month"))
                .andExpect(jsonPath("$.data.kpis").exists())
                .andExpect(jsonPath("$.data.kpis.netCollectedRevenue").isNumber())
                .andExpect(jsonPath("$.data.kpis.pendingRevenue").isNumber())
                .andExpect(jsonPath("$.data.cashflowChart").isArray())
                .andExpect(jsonPath("$.data.paymentMethods").isArray())
                .andExpect(jsonPath("$.data.paymentStatuses").isArray())
                .andExpect(jsonPath("$.data.recentTransactions").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/admin/dashboard/crm - Returns 200 with CRM metrics, tiers and top customers")
    void getCrmDashboard_success() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard/crm")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("period", "this-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.period").value("this-month"))
                .andExpect(jsonPath("$.data.kpis").exists())
                .andExpect(jsonPath("$.data.kpis.totalCustomers").isNumber())
                .andExpect(jsonPath("$.data.kpis.newCustomers").isNumber())
                .andExpect(jsonPath("$.data.kpis.activeBuyers").isNumber())
                .andExpect(jsonPath("$.data.kpis.repeatPurchaseRate").isNumber())
                .andExpect(jsonPath("$.data.customerGrowthChart").isArray())
                .andExpect(jsonPath("$.data.membershipTiers").isArray())
                .andExpect(jsonPath("$.data.topCustomers").isArray());
    }
}
