package vn.conganh.commercial.feature.payment.gateway;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SimulatePaymentRequest(
        @NotBlank(message = "Order code must not be blank")
        String orderCode,
        String transactionCode,
        BigDecimal amount
) {}
