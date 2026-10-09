package vn.conganh.commercial.feature.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerGrowthChartPoint {
    private String date;
    private Long newCustomersCount;
    private Long activeOrdersCount;
}
