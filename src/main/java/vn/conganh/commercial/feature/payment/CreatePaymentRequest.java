package vn.conganh.commercial.feature.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

public record CreatePaymentRequest(

        @NotNull(message = "Order ID is required")
        Long orderId,

        @NotNull(message = "Provider is required")
        PaymentProvider provider,

        String transactionCode,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00", message = "Amount must be >= 0")
        BigDecimal amount,

        @NotNull(message = "Status is required")
        PaymentStatus status,

        Instant paidAt
) {
}
