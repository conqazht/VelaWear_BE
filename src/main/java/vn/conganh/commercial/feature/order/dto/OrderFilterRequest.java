package vn.conganh.commercial.feature.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record OrderFilterRequest(
        Long userId,
        String orderCode,
        String status,
        String paymentMethod,
        String paymentStatus,
        String receiverName,
        String receiverPhone,
        BigDecimal finalAmountFrom,
        BigDecimal finalAmountTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedTo
) {
    public OrderFilterRequest withUserId(Long userId) {
        return new OrderFilterRequest(
                userId,
                orderCode,
                status,
                paymentMethod,
                paymentStatus,
                receiverName,
                receiverPhone,
                finalAmountFrom,
                finalAmountTo,
                createdFrom,
                createdTo,
                updatedFrom,
                updatedTo);
    }
}
