package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentPaymentTransaction {
    private Long orderId;
    private String orderCode;
    private String customerName;
    private String customerEmail;
    private String paymentMethod;
    private String paymentStatus;
    private BigDecimal finalAmount;
    private Instant createdAt;
}
