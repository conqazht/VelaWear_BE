package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CashflowChartPoint {
    private String date;
    private BigDecimal collectedAmount;
    private BigDecimal pendingAmount;
    private BigDecimal refundedAmount;
}
