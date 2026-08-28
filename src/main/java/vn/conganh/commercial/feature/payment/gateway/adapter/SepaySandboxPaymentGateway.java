package vn.conganh.commercial.feature.payment.gateway.adapter;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentGateway;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.feature.payment.sepay.SePayCheckoutForm;
import vn.conganh.commercial.feature.payment.sepay.SePayProperties;
import vn.conganh.commercial.feature.payment.sepay.SePayService;
import vn.conganh.commercial.util.constant.PaymentProvider;

@Component
@RequiredArgsConstructor
public class SepaySandboxPaymentGateway implements PaymentGateway {

    private final SePayService sePayService;
    private final SePayProperties properties;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.SEPAY;
    }

    @Override
    public boolean isAvailable() {
        return properties.isEnabled();
    }

    @Override
    public PaymentInitiationResult initiatePayment(Order order, Payment payment) {
        SePayCheckoutForm form = sePayService.createCheckoutForm(order);
        String txCode = "SEPAY-" + order.getOrderCode();
        return new PaymentInitiationResult(
                PaymentProvider.SEPAY,
                "BANK_TRANSFER",
                form.actionUrl(),
                form.fields(),
                txCode
        );
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature) {
        boolean valid = sePayService.hasValidSecret(signature);
        String orderCode = params.getOrDefault("order_invoice_number", params.get("orderCode"));
        String transactionId = params.getOrDefault("transaction_id", params.get("transactionId"));
        return PaymentCallbackResult.builder()
                .success(valid)
                .provider(PaymentProvider.SEPAY)
                .orderCode(orderCode)
                .transactionCode(transactionId)
                .message(valid ? "SePay payment verified" : "Invalid SePay signature")
                .responseCode(valid ? "00" : "97")
                .rawResponse(rawBody)
                .build();
    }
}
