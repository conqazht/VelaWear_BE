package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentStatusSummary {
    private String status;
    private Long count;
    private BigDecimal totalAmount;
    private Double percentage;
}
