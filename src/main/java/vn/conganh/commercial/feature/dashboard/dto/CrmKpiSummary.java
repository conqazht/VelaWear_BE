package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CrmKpiSummary {
    private Long totalCustomers;
    private Long newCustomers;
    private Double newCustomersChangePercentage;
    private Long activeBuyers;
    private Long repeatCustomerCount;
    private Double repeatPurchaseRate;
    private BigDecimal averageCustomerSpend;
}
