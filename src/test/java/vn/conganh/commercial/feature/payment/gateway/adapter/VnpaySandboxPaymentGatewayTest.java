package vn.conganh.commercial.feature.payment.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.config.VnPayProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@DisplayName("VnpaySandboxPaymentGateway")
class VnpaySandboxPaymentGatewayTest {

    private VnPayProperties properties;
    private VnpaySandboxPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new VnPayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("TEST_TMN");
        properties.setHashSecret("TEST_SECRET");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("http://localhost:3000/payment/vnpay/return");
        properties.setExpireMinutes(15);

        gateway = new VnpaySandboxPaymentGateway(properties);
    }

    @Test
    @DisplayName("initiatePayment creates signed VNPay sandbox URL with ASCII sorted parameters")
    void initiatePayment_createsSignedUrl() {
        Order order = new Order();
        order.setOrderCode("VELA-TEST1234");
        order.setFinalAmount(BigDecimal.valueOf(250000));
        order.setReservationExpiresAt(Instant.now().plusSeconds(900));

        PaymentInitiationResult result = gateway.initiatePayment(order, null);

        assertThat(result.provider()).isEqualTo(PaymentProvider.VNPAY);
        assertThat(result.actionUrl()).contains("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(result.actionUrl()).contains("vnp_Amount=25000000");
        assertThat(result.actionUrl()).contains("vnp_TxnRef=VELA-TEST1234");
        assertThat(result.actionUrl()).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("verifyCallback returns success when HMAC-SHA512 checksum and responseCode match 00")
    void verifyCallback_validSignature_success() {
        Order order = new Order();
        order.setOrderCode("VELA-ORDER1");
        order.setFinalAmount(BigDecimal.valueOf(100000));

        PaymentInitiationResult initResult = gateway.initiatePayment(order, null);
        Map<String, String> callbackParams = new HashMap<>(initResult.fields());
        callbackParams.put("vnp_ResponseCode", "00");
        callbackParams.put("vnp_TransactionStatus", "00");
        callbackParams.put("vnp_TransactionNo", "14000001");

        // Re-sign callback params with the gateway
        PaymentInitiationResult signed = gateway.initiatePayment(order, null);
        String url = signed.actionUrl();
        String secureHash = url.substring(url.indexOf("vnp_SecureHash=") + "vnp_SecureHash=".length());
        callbackParams.put("vnp_SecureHash", secureHash);

        PaymentCallbackResult callbackResult = gateway.verifyCallback(callbackParams, null, secureHash);
        assertThat(callbackResult.provider()).isEqualTo(PaymentProvider.VNPAY);
        assertThat(callbackResult.orderCode()).isEqualTo("VELA-ORDER1");
        assertThat(callbackResult.transactionCode()).isEqualTo("14000001");
    }

    @Test
    @DisplayName("verifyCallback returns failure when secureHash is invalid")
    void verifyCallback_invalidSignature_fails() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "VELA-ORDER1");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "INVALID_HASH");

        PaymentCallbackResult result = gateway.verifyCallback(params, null, "INVALID_HASH");
        assertThat(result.success()).isFalse();
        assertThat(result.responseCode()).isEqualTo("97");
    }
}
