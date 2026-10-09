package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopCustomerSummary {
    private Long userId;
    private String fullName;
    private String email;
    private String phone;
    private String membershipTier;
    private String tierLabel;
    private Long totalOrders;
    private BigDecimal totalSpent;
    private Instant lastOrderDate;
}
