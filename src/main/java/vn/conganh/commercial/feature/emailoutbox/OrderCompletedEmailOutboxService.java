package vn.conganh.commercial.feature.emailoutbox;

import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.feature.order.Order;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCompletedEmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Order order) {
        if (emailOutboxRepository.existsByEventTypeAndOrder_Id(EmailEventType.ORDER_COMPLETED, order.getId())) {
            return;
        }

        String recipientEmail = order.getUser().getEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("event=order_completed_email_enqueue outcome=skipped reason=missing_recipient orderId={}",
                    order.getId());
            return;
        }

        EmailOutbox message = new EmailOutbox();
        message.setEventType(EmailEventType.ORDER_COMPLETED);
        message.setOrder(order);
        message.setRecipientEmail(recipientEmail.trim().toLowerCase(Locale.ROOT));
        message.setTemplateKey(EmailTemplateKey.ORDER_COMPLETED);
        message.setStatus(EmailOutboxStatus.PENDING);
        message.setAttemptCount(0);
        message.setNextAttemptAt(Instant.now());
        emailOutboxRepository.save(message);
    }
}
