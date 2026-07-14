package vn.conganh.commercial.feature.payment.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransaction;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;
import vn.conganh.commercial.util.constant.PaymentTransactionStatus;

@ExtendWith(MockitoExtension.class)
class SePayServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTransactionRepository transactionRepository;
    @Mock
    private ObjectMapper objectMapper;

    private SePayService service;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        SePayProperties properties = new SePayProperties();
        properties.setEnabled(true);
        properties.setEnvironment("production");
        properties.setMerchantId("SP-LIVE-TEST");
        properties.setSecretKey("spsk_live_test-secret");
        properties.setCheckoutUrl("https://pay.sepay.vn/v1/checkout/init");
        properties.setSuccessUrl("https://shop.test/success");
        properties.setErrorUrl("https://shop.test/error");
        properties.setCancelUrl("https://shop.test/cancel");
        service = new SePayService(
                properties, orderRepository, paymentRepository, transactionRepository, objectMapper);

        order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setOrderCode("VELA-123");
        order.setFinalAmount(new BigDecimal("100000.00"));
        order.setPaymentStatus("UNPAID");

        payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 20L);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setAmount(order.getFinalAmount());
        payment.setStatus(PaymentStatus.PENDING);
    }

    @Test
    void createCheckoutForm_usesOfficialFieldOrderAndHmacSha256Signature() {
        SePayCheckoutForm result = service.createCheckoutForm(order);

        assertEquals("https://pay.sepay.vn/v1/checkout/init", result.actionUrl());
        assertEquals("BANK_TRANSFER", result.fields().get("payment_method"));
        assertEquals("VELA-123", result.fields().get("order_invoice_number"));
        assertEquals(
                "X8DQWY3kfN+VaNxyYkPGnhnu/0eYsq62+bD9p+g/ReY=",
                result.fields().get("signature"));
    }

    @Test
    void handleIpn_validPaidNotification_marksPaymentPaidAndRecordsTransaction() {
        SePayIpnRequest request = paidRequest(new BigDecimal("100000.00"));
        when(orderRepository.findWithLockByOrderCode("VELA-123")).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(10L)).thenReturn(Optional.of(payment));
        when(transactionRepository.findByTransactionCode("TX-123")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(request)).thenReturn("{}");

        service.handleIpn(request);

        assertEquals("PAID", order.getPaymentStatus());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("TX-123", payment.getTransactionCode());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(PaymentTransactionStatus.SUCCESS, captor.getValue().getStatus());
        assertEquals("TX-123", captor.getValue().getTransactionCode());
    }

    @Test
    void handleIpn_amountMismatch_rejectsNotificationWithoutUpdatingPayment() {
        SePayIpnRequest request = paidRequest(new BigDecimal("99999"));
        when(orderRepository.findWithLockByOrderCode("VELA-123")).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(10L)).thenReturn(Optional.of(payment));

        assertThrows(InvalidRequestException.class, () -> service.handleIpn(request));

        verify(transactionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleIpn_duplicateTransaction_isIdempotent() {
        SePayIpnRequest request = paidRequest(new BigDecimal("100000"));
        when(orderRepository.findWithLockByOrderCode("VELA-123")).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(10L)).thenReturn(Optional.of(payment));
        PaymentTransaction existing = new PaymentTransaction();
        existing.setPayment(payment);
        existing.setTransactionCode("TX-123");
        when(transactionRepository.findByTransactionCode("TX-123")).thenReturn(Optional.of(existing));

        service.handleIpn(request);

        verify(transactionRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    private SePayIpnRequest paidRequest(BigDecimal amount) {
        return new SePayIpnRequest(
                1_759_134_682L,
                "ORDER_PAID",
                new SePayIpnRequest.OrderData(
                        "ORDER-ID", "SEPAY-ORDER", "CAPTURED", "VND", amount,
                        "VELA-123", null, "Thanh toan"),
                new SePayIpnRequest.TransactionData(
                        "TRANSACTION-ID", "BANK_TRANSFER", "TX-123", "PAYMENT",
                        "2025-09-29 15:31:22", "APPROVED", amount, "VND",
                        "AUTHENTICATION_SUCCESSFUL"));
    }
}
