package vn.conganh.commercial.feature.emailoutbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.feature.auth.email.EmailDeliveryException;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.emailoutbox.EmailOutboxStore.Claim;
import vn.conganh.commercial.feature.emailoutbox.OrderCompletedEmailTemplateRenderer.RenderedEmail;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailOutboxJob")
class EmailOutboxJobTest {

    @Mock
    private EmailOutboxStore emailOutboxStore;

    @Mock
    private OrderCompletedEmailTemplateRenderer templateRenderer;

    @Mock
    private EmailProvider emailProvider;

    private EmailOutboxJob job;
    private EmailNotificationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EmailNotificationProperties(
                "https://shop.velawear.test",
                new EmailNotificationProperties.Outbox(
                        true,
                        10_000,
                        10,
                        3,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(1),
                        Duration.ofHours(1)));
        job = new EmailOutboxJob(
                emailOutboxStore,
                templateRenderer,
                emailProvider,
                properties);
    }

    @Test
    @DisplayName("successful delivery records provider id and sent state")
    void deliverPendingEmails_success_marksSent() {
        Claim claim = claim(1);
        RenderedEmail renderedEmail = new RenderedEmail("Subject", "<p>Body</p>");
        when(emailOutboxStore.claimBatch(any(Instant.class), eq(1), eq(Duration.ofMinutes(2))))
                .thenReturn(List.of(claim), List.of());
        when(templateRenderer.render(claim)).thenReturn(renderedEmail);
        when(emailProvider.sendEmail(
                "customer@example.com",
                "Subject",
                "<p>Body</p>",
                "order-completed/10"))
                .thenReturn("provider-message-id");
        when(emailOutboxStore.markSent(
                eq(100L),
                eq(claim.claimToken()),
                eq("provider-message-id"),
                any(Instant.class)))
                .thenReturn(true);

        job.deliverPendingEmails();

        verify(emailOutboxStore).markSent(
                eq(100L),
                eq(claim.claimToken()),
                eq("provider-message-id"),
                any(Instant.class));
        verify(emailOutboxStore, never()).markFailure(
                any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("transient delivery failure schedules a retry")
    void deliverPendingEmails_transientFailure_schedulesRetry() {
        Claim claim = claim(1);
        when(emailOutboxStore.claimBatch(any(Instant.class), eq(1), eq(Duration.ofMinutes(2))))
                .thenReturn(List.of(claim), List.of());
        when(templateRenderer.render(claim)).thenThrow(new IllegalStateException("temporary"));
        when(emailOutboxStore.markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(false),
                any(Instant.class),
                any(String.class),
                any(Instant.class)))
                .thenReturn(true);

        job.deliverPendingEmails();

        verify(emailOutboxStore).markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(false),
                any(Instant.class),
                eq("IllegalStateException: temporary"),
                any(Instant.class));
    }

    @Test
    @DisplayName("last allowed attempt moves the message to failed")
    void deliverPendingEmails_lastAttempt_marksFailed() {
        Claim claim = claim(3);
        when(emailOutboxStore.claimBatch(any(Instant.class), eq(1), eq(Duration.ofMinutes(2))))
                .thenReturn(List.of(claim), List.of());
        when(templateRenderer.render(claim)).thenThrow(new IllegalStateException("permanent"));
        when(emailOutboxStore.markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(true),
                any(Instant.class),
                any(String.class),
                any(Instant.class)))
                .thenReturn(true);

        job.deliverPendingEmails();

        verify(emailOutboxStore).markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(true),
                any(Instant.class),
                eq("IllegalStateException: permanent"),
                any(Instant.class));
    }

    @Test
    @DisplayName("permanent provider rejection fails immediately without wasting retries")
    void deliverPendingEmails_permanentProviderFailure_marksFailedImmediately() {
        Claim claim = claim(1);
        RenderedEmail renderedEmail = new RenderedEmail("Subject", "<p>Body</p>");
        when(emailOutboxStore.claimBatch(any(Instant.class), eq(1), eq(Duration.ofMinutes(2))))
                .thenReturn(List.of(claim), List.of());
        when(templateRenderer.render(claim)).thenReturn(renderedEmail);
        when(emailProvider.sendEmail(
                "customer@example.com",
                "Subject",
                "<p>Body</p>",
                "order-completed/10"))
                .thenThrow(new EmailDeliveryException("status=422", false));
        when(emailOutboxStore.markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(true),
                any(Instant.class),
                any(String.class),
                any(Instant.class)))
                .thenReturn(true);

        job.deliverPendingEmails();

        verify(emailOutboxStore).markFailure(
                eq(100L),
                eq(claim.claimToken()),
                eq(true),
                any(Instant.class),
                eq("EmailDeliveryException: status=422"),
                any(Instant.class));
    }

    private Claim claim(int attemptCount) {
        return new Claim(
                100L,
                10L,
                "customer@example.com",
                EmailTemplateKey.ORDER_COMPLETED,
                attemptCount,
                Instant.parse("2026-07-16T12:30:00Z"),
                UUID.randomUUID());
    }
}
