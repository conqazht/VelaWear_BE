package vn.conganh.commercial.feature.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

public record PaymentFilterRequest(
        Long orderId,
        PaymentProvider provider,
        String transactionCode,
        PaymentStatus status,
        BigDecimal amountFrom,
        BigDecimal amountTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate paidFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate paidTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate updatedTo
) {}
