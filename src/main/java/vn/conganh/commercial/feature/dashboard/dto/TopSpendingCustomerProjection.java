package vn.conganh.commercial.feature.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

public interface TopSpendingCustomerProjection {
    Long getUserId();
    String getFullName();
    String getEmail();
    String getReceiverPhone();
    Long getTotalOrders();
    BigDecimal getTotalSpent();
    Instant getLastOrderDate();
}
