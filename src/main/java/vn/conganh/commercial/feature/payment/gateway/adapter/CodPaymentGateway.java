package vn.conganh.commercial.feature.payment.gateway.adapter;

import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentGateway;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Component
public class CodPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.COD;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public PaymentInitiationResult initiatePayment(Order order, Payment payment) {
        String txCode = "COD-" + order.getOrderCode();
        return new PaymentInitiationResult(
                PaymentProvider.COD,
                "COD",
                null,
                Collections.emptyMap(),
                txCode
        );
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature) {
        String orderCode = params.getOrDefault("orderCode", params.get("order_code"));
        return PaymentCallbackResult.builder()
                .success(true)
                .provider(PaymentProvider.COD)
                .orderCode(orderCode)
                .transactionCode("COD-" + orderCode)
                .message("COD payment recorded")
                .responseCode("00")
                .rawResponse(rawBody)
                .build();
    }
}
