package vn.conganh.commercial.feature.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.emailoutbox.CommerceEmailOutboxService;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransaction;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCallbackService")
class PaymentCallbackServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private CommerceEmailOutboxService commerceEmailOutboxService;

    private PaymentCallbackService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCallbackService(
                orderRepository,
                paymentRepository,
                paymentTransactionRepository,
                commerceEmailOutboxService
        );
    }

    @Test
    @DisplayName("processCallback updates Order to PAID and enqueues PaymentSucceeded email on success")
    void processCallback_success_updatesOrderAndEnqueuesEmail() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setOrderCode("VELA-ORD100");
        order.setPaymentStatus("UNPAID");

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 200L);
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(300000));

        when(orderRepository.findWithLockByOrderCode("VELA-ORD100")).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(100L)).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.findByTransactionCode("TX-12345")).thenReturn(Optional.empty());

        PaymentCallbackResult callback = PaymentCallbackResult.builder()
                .success(true)
                .provider(PaymentProvider.VNPAY)
                .orderCode("VELA-ORD100")
                .transactionCode("TX-12345")
                .amount(BigDecimal.valueOf(300000))
                .rawResponse("{\"rsp\": \"00\"}")
                .build();

        PaymentCallbackResult result = service.processCallback(callback);

        assertThat(result.success()).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo("PAID");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTransactionCode()).isEqualTo("TX-12345");

        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
        verify(commerceEmailOutboxService).enqueuePaymentSucceeded(order);
    }

    @Test
    @DisplayName("processCallback is idempotent when payment is already in terminal SUCCESS state")
    void processCallback_idempotent_noDoubleStateChange() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setOrderCode("VELA-ORD100");
        order.setPaymentStatus("PAID");

        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 200L);
        payment.setOrder(order);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setTransactionCode("TX-12345");

        when(orderRepository.findWithLockByOrderCode("VELA-ORD100")).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(100L)).thenReturn(Optional.of(payment));

        PaymentCallbackResult callback = PaymentCallbackResult.builder()
                .success(true)
                .provider(PaymentProvider.VNPAY)
                .orderCode("VELA-ORD100")
                .transactionCode("TX-12345")
                .amount(BigDecimal.valueOf(300000))
                .build();

        PaymentCallbackResult result = service.processCallback(callback);

        assertThat(result.success()).isTrue();
        verify(orderRepository, never()).save(any());
        verify(commerceEmailOutboxService, never()).enqueuePaymentSucceeded(any());
    }
}
