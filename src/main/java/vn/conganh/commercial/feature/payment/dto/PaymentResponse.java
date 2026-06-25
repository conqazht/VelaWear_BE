package vn.conganh.commercial.feature.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

public record PaymentResponse(
        Long id,
        Long orderId,
        String orderCode,
        PaymentProvider provider,
        String transactionCode,
        BigDecimal amount,
        PaymentStatus status,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderCode(),
                payment.getProvider(),
                payment.getTransactionCode(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
