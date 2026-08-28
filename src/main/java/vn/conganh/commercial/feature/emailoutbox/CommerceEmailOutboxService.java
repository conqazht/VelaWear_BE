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
public class CommerceEmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueOrderCreated(Order order) {
        enqueue(order, EmailEventType.ORDER_CREATED, EmailTemplateKey.ORDER_CREATED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePaymentSucceeded(Order order) {
        enqueue(order, EmailEventType.PAYMENT_SUCCEEDED, EmailTemplateKey.PAYMENT_SUCCEEDED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePaymentFailed(Order order) {
        enqueue(order, EmailEventType.PAYMENT_FAILED, EmailTemplateKey.PAYMENT_FAILED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueOrderCompleted(Order order) {
        enqueue(order, EmailEventType.ORDER_COMPLETED, EmailTemplateKey.ORDER_COMPLETED);
    }

    private void enqueue(Order order, EmailEventType eventType, EmailTemplateKey templateKey) {
        if (order == null || order.getId() == null) {
            return;
        }

        if (emailOutboxRepository.existsByEventTypeAndOrder_Id(eventType, order.getId())) {
            log.debug("event=email_enqueue outcome=skipped reason=already_enqueued eventType={} orderId={}",
                    eventType, order.getId());
            return;
        }

        String recipientEmail = null;
        if (order.getUser() != null && order.getUser().getEmail() != null) {
            recipientEmail = order.getUser().getEmail();
        } else if (order.getReceiverPhone() != null) {
            recipientEmail = null;
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("event=email_enqueue outcome=skipped reason=missing_recipient eventType={} orderId={}",
                    eventType, order.getId());
            return;
        }

        EmailOutbox message = new EmailOutbox();
        message.setEventType(eventType);
        message.setOrder(order);
        message.setRecipientEmail(recipientEmail.trim().toLowerCase(Locale.ROOT));
        message.setTemplateKey(templateKey);
        message.setStatus(EmailOutboxStatus.PENDING);
        message.setAttemptCount(0);
        message.setNextAttemptAt(Instant.now());
        emailOutboxRepository.save(message);

        log.info("event=email_enqueue outcome=enqueued eventType={} orderId={} recipient={}",
                eventType, order.getId(), recipientEmail);
    }
}
