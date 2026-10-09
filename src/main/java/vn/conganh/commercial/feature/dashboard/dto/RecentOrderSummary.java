package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentOrderSummary {
    private Long id;
    private String orderCode;
    private String customerName;
    private String customerEmail;
    private Instant createdAt;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private BigDecimal totalAmount;
}
