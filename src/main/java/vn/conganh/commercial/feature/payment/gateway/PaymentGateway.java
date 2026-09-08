package vn.conganh.commercial.feature.payment.gateway;

import java.util.Map;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.util.constant.PaymentProvider;

public interface PaymentGateway {

    PaymentProvider getProvider();

    boolean isAvailable();

    PaymentInitiationResult initiatePayment(Order order, Payment payment);

    PaymentCallbackResult verifyCallback(Map<String, String> params, String rawBody, String signature);
}
