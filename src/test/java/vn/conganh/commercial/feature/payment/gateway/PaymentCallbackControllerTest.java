package vn.conganh.commercial.feature.payment.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.conganh.commercial.feature.payment.config.PaymentEnvironmentProperties;
import vn.conganh.commercial.util.constant.PaymentProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCallbackController")
class PaymentCallbackControllerTest {

    @Mock
    private PaymentGatewayRouter paymentGatewayRouter;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentCallbackService paymentCallbackService;

    private PaymentEnvironmentProperties environmentProperties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        environmentProperties = new PaymentEnvironmentProperties();
        environmentProperties.getSimulation().setEnabled(true);

        PaymentCallbackController controller = new PaymentCallbackController(
                paymentGatewayRouter,
                paymentCallbackService,
                environmentProperties
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/payments/vnpay/callback processes return URL and returns 200 OK")
    void vnpayCallback_returnsOk() throws Exception {
        when(paymentGatewayRouter.getGateway(PaymentProvider.VNPAY)).thenReturn(paymentGateway);
        when(paymentGateway.verifyCallback(any(), any(), any())).thenReturn(
                PaymentCallbackResult.builder()
                        .success(true)
                        .provider(PaymentProvider.VNPAY)
                        .orderCode("VELA-ORD1")
                        .transactionCode("TX-1")
                        .build()
        );
        when(paymentCallbackService.processCallback(any())).thenReturn(
                PaymentCallbackResult.builder()
                        .success(true)
                        .provider(PaymentProvider.VNPAY)
                        .orderCode("VELA-ORD1")
                        .transactionCode("TX-1")
                        .build()
        );

        mockMvc.perform(get("/api/v1/payments/vnpay/callback")
                        .param("vnp_TxnRef", "VELA-ORD1")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "TEST_HASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.orderCode").value("VELA-ORD1"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/vnpay/ipn returns VNPay standard json format")
    void vnpayIpn_returnsStandardJson() throws Exception {
        when(paymentGatewayRouter.getGateway(PaymentProvider.VNPAY)).thenReturn(paymentGateway);
        when(paymentGateway.verifyCallback(any(), any(), any())).thenReturn(
                PaymentCallbackResult.builder()
                        .success(true)
                        .provider(PaymentProvider.VNPAY)
                        .orderCode("VELA-ORD1")
                        .responseCode("00")
                        .build()
        );

        mockMvc.perform(post("/api/v1/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "VELA-ORD1")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "TEST_HASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/simulate/success simulates payment successfully")
    void simulateSuccess_returnsSuccess() throws Exception {
        when(paymentCallbackService.processCallback(any())).thenReturn(
                PaymentCallbackResult.builder()
                        .success(true)
                        .provider(PaymentProvider.VNPAY)
                        .orderCode("VELA-SIM1")
                        .transactionCode("SIM-1234")
                        .amount(BigDecimal.valueOf(200000))
                        .build()
        );

        mockMvc.perform(post("/api/v1/payments/simulate/success")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderCode": "VELA-SIM1",
                                  "amount": 200000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.orderCode").value("VELA-SIM1"))
                .andExpect(jsonPath("$.data.success").value(true));
    }
}
