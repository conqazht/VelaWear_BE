package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RevenueChartPoint {
    private String date;
    private BigDecimal revenue;
    private Long orderCount;
}
