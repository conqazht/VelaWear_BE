package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentMethodSummary {
    private String method;
    private Long count;
    private BigDecimal totalAmount;
    private Double percentage;
}
