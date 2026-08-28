package vn.conganh.commercial.feature.payment.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.config.StripeProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@DisplayName("StripeTestPaymentGateway")
class StripeTestPaymentGatewayTest {

    private StripeProperties properties;
    private StripeTestPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setEnabled(true);
        properties.setSecretKey("sk_test_mock_secret_key_12345");
        properties.setWebhookSecret("whsec_mock_webhook_secret_12345");
        properties.setSuccessUrl("http://localhost:3000/payment/stripe/success?session_id={CHECKOUT_SESSION_ID}&order_code={ORDER_CODE}");
        properties.setCancelUrl("http://localhost:3000/payment/stripe/cancel?order_code={ORDER_CODE}");
        properties.setCurrency("vnd");

        gateway = new StripeTestPaymentGateway(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("initiatePayment resolves fallback URL gracefully on mock keys")
    void initiatePayment_createsSessionFallback() {
        Order order = new Order();
        order.setOrderCode("VELA-STRIPE123");
        order.setFinalAmount(BigDecimal.valueOf(500000));

        PaymentInitiationResult result = gateway.initiatePayment(order, null);
        assertThat(result.provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(result.actionUrl()).contains("order_code=VELA-STRIPE123");
        assertThat(result.actionUrl()).contains("session_id=cs_test_VELA-STRIPE123");
    }

    @Test
    @DisplayName("verifyCallback parses Stripe checkout.session.completed JSON correctly")
    void verifyCallback_parsesJsonWebhook() {
        String payload = """
                {
                  "id": "evt_test123",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_session_999",
                      "amount_total": 500000,
                      "metadata": {
                        "orderCode": "VELA-STRIPE999"
                      }
                    }
                  }
                }
                """;

        PaymentCallbackResult result = gateway.verifyCallback(Collections.emptyMap(), payload, null);
        assertThat(result.provider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(result.orderCode()).isEqualTo("VELA-STRIPE999");
        assertThat(result.transactionCode()).isEqualTo("cs_test_session_999");
        assertThat(result.amount()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(result.success()).isTrue();
    }
}
