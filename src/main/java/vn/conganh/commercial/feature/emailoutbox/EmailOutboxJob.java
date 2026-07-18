package vn.conganh.commercial.feature.emailoutbox;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.conganh.commercial.feature.auth.email.EmailDeliveryException;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.emailoutbox.EmailOutboxStore.Claim;
import vn.conganh.commercial.feature.emailoutbox.OrderCompletedEmailTemplateRenderer.RenderedEmail;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.email.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EmailOutboxJob {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final EmailOutboxStore emailOutboxStore;
    private final OrderCompletedEmailTemplateRenderer templateRenderer;
    private final EmailProvider emailProvider;
    private final EmailNotificationProperties properties;

    @Scheduled(fixedDelayString = "${app.email.outbox.scan-ms:10000}")
    public void deliverPendingEmails() {
        for (int processed = 0; processed < properties.outbox().batchSize(); processed++) {
            Claim claim = claimNext();
            if (claim == null) {
                return;
            }
            try {
                deliver(claim);
            } catch (RuntimeException exception) {
                log.error("event=email_outbox_delivery outcome=state_update_failure outboxId={} errorType={}",
                        claim.id(), exception.getClass().getSimpleName());
            }
        }
    }

    private Claim claimNext() {
        try {
            return emailOutboxStore
                    .claimBatch(Instant.now(), 1, properties.outbox().leaseDuration())
                    .stream()
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.error("event=email_outbox_claim outcome=failure errorType={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private void deliver(Claim claim) {
        RenderedEmail renderedEmail;
        String providerMessageId;
        try {
            renderedEmail = templateRenderer.render(claim);
            providerMessageId = emailProvider.sendEmail(
                    claim.recipientEmail(),
                    renderedEmail.subject(),
                    renderedEmail.html(),
                    "order-completed/" + claim.orderId());
        } catch (RuntimeException exception) {
            handleDeliveryFailure(claim, exception);
            return;
        }

        boolean updated = emailOutboxStore.markSent(
                claim.id(),
                claim.claimToken(),
                providerMessageId,
                Instant.now());
        if (!updated) {
            log.warn("event=email_outbox_delivery outcome=stale_claim outboxId={}", claim.id());
            return;
        }
        log.info("event=email_outbox_delivery outcome=sent outboxId={} attempt={}",
                claim.id(), claim.attemptCount());
    }

    private void handleDeliveryFailure(Claim claim, RuntimeException exception) {
        boolean permanentProviderFailure = exception instanceof EmailDeliveryException deliveryException
                && !deliveryException.isRetryable();
        boolean terminal = permanentProviderFailure
                || claim.attemptCount() >= properties.outbox().maxAttempts();
        Instant now = Instant.now();
        Instant nextAttemptAt = terminal
                ? now
                : now.plus(properties.outbox().retryDelayForAttempt(claim.attemptCount()));
        boolean updated = emailOutboxStore.markFailure(
                claim.id(),
                claim.claimToken(),
                terminal,
                nextAttemptAt,
                summarize(exception),
                now);
        if (!updated) {
            log.warn("event=email_outbox_delivery outcome=stale_failure_claim outboxId={}", claim.id());
            return;
        }

        if (terminal) {
            log.error("event=email_outbox_delivery outcome=failed outboxId={} attempt={} errorType={}",
                    claim.id(), claim.attemptCount(), exception.getClass().getSimpleName());
        } else {
            log.warn("event=email_outbox_delivery outcome=retry_scheduled outboxId={} attempt={} errorType={}",
                    claim.id(), claim.attemptCount(), exception.getClass().getSimpleName());
        }
    }

    private String summarize(RuntimeException exception) {
        String message = exception.getClass().getSimpleName();
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            message += ": " + exception.getMessage();
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
