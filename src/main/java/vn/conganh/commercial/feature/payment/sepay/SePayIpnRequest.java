package vn.conganh.commercial.feature.payment.sepay;

import java.math.BigDecimal;

public record SePayIpnRequest(
        Long timestamp,
        String notification_type,
        OrderData order,
        TransactionData transaction
) {
    public record OrderData(
            String id,
            String order_id,
            String order_status,
            String order_currency,
            BigDecimal order_amount,
            String order_invoice_number,
            Object custom_data,
            String order_description
    ) {}

    public record TransactionData(
            String id,
            String payment_method,
            String transaction_id,
            String transaction_type,
            String transaction_date,
            String transaction_status,
            BigDecimal transaction_amount,
            String transaction_currency,
            String authentication_status
    ) {}
}
