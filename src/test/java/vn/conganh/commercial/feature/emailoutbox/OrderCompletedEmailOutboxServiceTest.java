package vn.conganh.commercial.feature.emailoutbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.feature.order.Order;
import vn.conganh.commercial.feature.user.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCompletedEmailOutboxService")
class OrderCompletedEmailOutboxServiceTest {

    @Mock
    private EmailOutboxRepository emailOutboxRepository;

    private OrderCompletedEmailOutboxService service;
    private CommerceEmailOutboxService commerceEmailOutboxService;

    @BeforeEach
    void setUp() {
        commerceEmailOutboxService = new CommerceEmailOutboxService(emailOutboxRepository);
        service = new OrderCompletedEmailOutboxService(commerceEmailOutboxService);
    }

    @Test
    @DisplayName("enqueue stores one pending message with a recipient snapshot")
    void enqueue_newEvent_savesPendingMessage() {
        Order order = order();
        when(emailOutboxRepository.existsByEventTypeAndOrder_Id(EmailEventType.ORDER_COMPLETED, 10L))
                .thenReturn(false);

        service.enqueue(order);

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(emailOutboxRepository).save(captor.capture());
        EmailOutbox message = captor.getValue();
        assertThat(message.getEventType()).isEqualTo(EmailEventType.ORDER_COMPLETED);
        assertThat(message.getTemplateKey()).isEqualTo(EmailTemplateKey.ORDER_COMPLETED);
        assertThat(message.getOrder()).isSameAs(order);
        assertThat(message.getRecipientEmail()).isEqualTo("customer@example.com");
        assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("enqueue is idempotent for an existing order completed event")
    void enqueue_existingEvent_doesNotSaveAgain() {
        Order order = order();
        when(emailOutboxRepository.existsByEventTypeAndOrder_Id(EmailEventType.ORDER_COMPLETED, 10L))
                .thenReturn(true);

        service.enqueue(order);

        verify(emailOutboxRepository, never()).save(any());
    }

    private Order order() {
        User user = new User();
        user.setEmail(" Customer@Example.com ");

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setUser(user);
        order.setOrderCode("ORD-10");
        order.setStatus("COMPLETED");
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.valueOf(100000));
        order.setReceiverName("Nguyen Van A");
        order.setReceiverPhone("0900000000");
        order.setReceiverAddress("123 Test");
        order.setPaymentMethod("COD");
        order.setPaymentStatus("PAID");
        return order;
    }
}
