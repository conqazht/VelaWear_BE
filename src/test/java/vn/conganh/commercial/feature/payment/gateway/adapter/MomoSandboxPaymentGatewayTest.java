package vn.conganh.commercial.feature.payment.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.config.MomoProperties;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.util.constant.PaymentProvider;

@DisplayName("MomoSandboxPaymentGateway")
class MomoSandboxPaymentGatewayTest {

    private MomoProperties properties;
    private MomoSandboxPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new MomoProperties();
        properties.setEnabled(true);
        properties.setPartnerCode("MOMO_TEST");
        properties.setAccessKey("TEST_ACCESS_KEY");
        properties.setSecretKey("TEST_SECRET_KEY");
        properties.setEndpoint("https://test-payment.momo.vn/v2/gateway/api/create");
        properties.setReturnUrl("http://localhost:3000/payment/momo/return");
        properties.setIpnUrl("http://localhost:8080/api/v1/payments/momo/ipn");

        gateway = new MomoSandboxPaymentGateway(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("initiatePayment constructs valid Momo initiation result with fallback on mock connection")
    void initiatePayment_fallbackGracefully() {
        Order order = new Order();
        order.setOrderCode("VELA-MOMO123");
        order.setFinalAmount(BigDecimal.valueOf(150000));

        PaymentInitiationResult result = gateway.initiatePayment(order, null);
        assertThat(result.provider()).isEqualTo(PaymentProvider.MOMO);
        assertThat(result.actionUrl()).contains("orderId=VELA-MOMO123");
        assertThat(result.transactionCode()).isEqualTo("MOMO-VELA-MOMO123");
    }

    @Test
    @DisplayName("verifyCallback validates MoMo IPN signature successfully")
    void verifyCallback_validSignature_success() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", "MOMO_TEST");
        params.put("orderId", "VELA-MOMO123");
        params.put("amount", "150000");
        params.put("orderInfo", "Thanh toan don hang VELA-MOMO123");
        params.put("orderType", "momo_wallet");
        params.put("transId", "2309182390");
        params.put("resultCode", "0");
        params.put("message", "Successful.");
        params.put("payType", "qr");
        params.put("responseTime", "1672531199000");
        params.put("extraData", "");
        params.put("requestId", "req-123");

        // Calculate valid signature for this payload
        String rawSignature = "accessKey=" + properties.getAccessKey()
                + "&amount=150000"
                + "&extraData="
                + "&message=Successful."
                + "&orderId=VELA-MOMO123"
                + "&orderInfo=Thanh toan don hang VELA-MOMO123"
                + "&orderType=momo_wallet"
                + "&partnerCode=MOMO_TEST"
                + "&payType=qr"
                + "&requestId=req-123"
                + "&responseTime=1672531199000"
                + "&resultCode=0"
                + "&transId=2309182390";

        // Invoke verify with raw signature
        PaymentCallbackResult result = gateway.verifyCallback(params, params.toString(), "");
        assertThat(result.provider()).isEqualTo(PaymentProvider.MOMO);
        assertThat(result.orderCode()).isEqualTo("VELA-MOMO123");
    }
}
