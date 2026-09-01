package vn.conganh.commercial.feature.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.feature.emailoutbox.CommerceEmailTemplateRenderer;
import vn.conganh.commercial.feature.emailoutbox.EmailEventType;
import vn.conganh.commercial.feature.emailoutbox.EmailOutbox;
import vn.conganh.commercial.feature.emailoutbox.EmailOutboxRepository;
import vn.conganh.commercial.feature.emailoutbox.EmailOutboxStore;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.order.OrderRepository;
import vn.conganh.commercial.feature.payment.Payment;
import vn.conganh.commercial.feature.payment.PaymentRepository;
import vn.conganh.commercial.feature.payment.PaymentTransaction;
import vn.conganh.commercial.feature.payment.PaymentTransactionRepository;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.PaymentProvider;
import vn.conganh.commercial.util.constant.PaymentStatus;

@DisplayName("Payment Flow Integration Test")
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private PaymentCallbackService callbackService;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private CommerceEmailTemplateRenderer templateRenderer;

    @Test
    @Transactional
    @DisplayName("Full Flow: MoMo Callback -> Order PAID -> Payment SUCCESS -> Email Outbox enqueued & rendered")
    void fullPaymentFlow_success_transitionsOrderAndEnqueuesRenderableEmail() {
        User user = userRepository.findAll().stream().findFirst().orElseThrow();
        String uniqueCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);

        Order order = new Order();
        order.setUser(user);
        order.setOrderCode(uniqueCode);
        order.setStatus("PENDING");
        order.setPaymentStatus("UNPAID");
        order.setPaymentMethod("MOMO");
        order.setSubtotal(BigDecimal.valueOf(150000));
        order.setFinalAmount(BigDecimal.valueOf(150000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setReceiverName("Nguyễn Văn Test");
        order.setReceiverPhone("0987654321");
        order.setReceiverAddress("123 Phố Test, Hà Nội");
        order = orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.MOMO);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(150000));
        payment = paymentRepository.save(payment);

        PaymentCallbackResult callback = PaymentCallbackResult.builder()
                .success(true)
                .provider(PaymentProvider.MOMO)
                .orderCode(uniqueCode)
                .transactionCode("MOMO-TX-" + uniqueCode)
                .amount(BigDecimal.valueOf(150000))
                .rawResponse("{\"resultCode\": 0, \"message\": \"Thanh cong\"}")
                .build();

        PaymentCallbackResult result = callbackService.processCallback(callback);

        assertThat(result.success()).isTrue();

        Order updatedOrder = orderRepository.findByOrderCode(uniqueCode).orElseThrow();
        assertThat(updatedOrder.getPaymentStatus()).isEqualTo("PAID");

        Payment updatedPayment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(updatedPayment.getTransactionCode()).isEqualTo("MOMO-TX-" + uniqueCode);
        assertThat(updatedPayment.getPaidAt()).isNotNull();

        PaymentTransaction tx = transactionRepository.findByTransactionCode("MOMO-TX-" + uniqueCode).orElseThrow();
        assertThat(tx.getGatewayResponse()).contains("Thanh cong");

        var outboxList = emailOutboxRepository.findAll().stream()
                .filter(o -> uniqueCode.equals(o.getOrder().getOrderCode()) && o.getEventType() == EmailEventType.PAYMENT_SUCCEEDED)
                .toList();
        assertThat(outboxList).isNotEmpty();

        EmailOutbox outbox = outboxList.getFirst();
        assertThat(outbox.getRecipientEmail()).isEqualTo(user.getEmail());

        EmailOutboxStore.Claim claim = new EmailOutboxStore.Claim(
                outbox.getId(),
                outbox.getOrder().getId(),
                outbox.getRecipientEmail(),
                outbox.getTemplateKey(),
                outbox.getAttemptCount(),
                java.time.Instant.now(),
                java.util.UUID.randomUUID()
        );

        CommerceEmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(claim);
        assertThat(rendered.subject()).contains(uniqueCode);
        assertThat(rendered.html()).contains("Nguyễn Văn Test");
        assertThat(rendered.html()).contains(uniqueCode);
    }
}
