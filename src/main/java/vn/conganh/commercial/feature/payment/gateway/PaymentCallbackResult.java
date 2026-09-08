package vn.conganh.commercial.feature.payment.gateway;

import java.math.BigDecimal;
import lombok.Builder;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Builder
public record PaymentCallbackResult(
        boolean success,
        PaymentProvider provider,
        String orderCode,
        String transactionCode,
        BigDecimal amount,
        String message,
        String responseCode,
        String rawResponse
) {}
