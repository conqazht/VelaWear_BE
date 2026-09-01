package vn.conganh.commercial.feature.payment.gateway.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.payment.gateway.PaymentCallbackResult;
import vn.conganh.commercial.feature.payment.gateway.PaymentInitiationResult;
import vn.conganh.commercial.feature.payment.sepay.SePayCheckoutForm;
import vn.conganh.commercial.feature.payment.sepay.SePayProperties;
import vn.conganh.commercial.feature.payment.sepay.SePayService;
import vn.conganh.commercial.util.constant.PaymentProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("SepaySandboxPaymentGateway")
class SepaySandboxPaymentGatewayTest {

    @Mock
    private SePayService sePayService;

    private SePayProperties properties;
    private SepaySandboxPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new SePayProperties();
        properties.setEnabled(true);
        gateway = new SepaySandboxPaymentGateway(sePayService, properties);
    }

    @Test
    @DisplayName("initiatePayment delegates to SePayService and returns formatted result")
    void initiatePayment_delegatesToService() {
        Order order = new Order();
        order.setOrderCode("VELA-SEP1");
        order.setFinalAmount(BigDecimal.valueOf(250000));

        when(sePayService.createCheckoutForm(any())).thenReturn(
                new SePayCheckoutForm(
                        "https://pay.sepay.vn/v1/checkout/init",
                        Map.of("order_invoice_number", "VELA-SEP1", "amount", "250000")
                )
        );

        PaymentInitiationResult result = gateway.initiatePayment(order, null);

        assertThat(result.provider()).isEqualTo(PaymentProvider.SEPAY);
        assertThat(result.paymentMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(result.actionUrl()).isEqualTo("https://pay.sepay.vn/v1/checkout/init");
        assertThat(result.fields()).containsEntry("order_invoice_number", "VELA-SEP1");
    }

    @Test
    @DisplayName("verifyCallback validates signature via SePayService")
    void verifyCallback_validSignature_returnsSuccess() {
        when(sePayService.hasValidSecret("VALID_SECRET")).thenReturn(true);

        PaymentCallbackResult result = gateway.verifyCallback(
                Map.of("order_invoice_number", "VELA-SEP1", "transaction_id", "TX-SEP-1"),
                "{\"status\":\"PAID\"}",
                "VALID_SECRET"
        );

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(PaymentProvider.SEPAY);
        assertThat(result.orderCode()).isEqualTo("VELA-SEP1");
        assertThat(result.transactionCode()).isEqualTo("TX-SEP-1");
    }
}
