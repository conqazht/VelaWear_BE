package vn.conganh.commercial.feature.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.feature.auth.email.smtp.SmtpMailProperties;

@ExtendWith(MockitoExtension.class)
@DisplayName("GmailSmtpEmailProvider Test")
class GmailSmtpEmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    private GmailSmtpEmailProvider emailProvider;
    private SmtpMailProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SmtpMailProperties("conganhtruongw@gmail.com", "Vela Wear");
        emailProvider = new GmailSmtpEmailProvider(mailSender, properties);
    }

    @Test
    @DisplayName("Should send email successfully")
    void shouldSendEmailSuccessfully() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String messageId = emailProvider.sendEmail(
                "receiver@example.com", "Test Subject", "<p>Test Content</p>");

        assertThat(messageId).isNotBlank();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should send email with idempotency key")
    void shouldSendEmailWithIdempotencyKey() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String messageId = emailProvider.sendEmail(
                "receiver@example.com", "Test Subject", "<p>Test Content</p>", "order-completed/10");

        assertThat(messageId).isEqualTo("order-completed/10");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when sender email is missing")
    void shouldThrowExceptionWhenSenderEmailMissing() {
        SmtpMailProperties emptyProperties = new SmtpMailProperties("", "Vela Wear");
        GmailSmtpEmailProvider unconfiguredProvider = new GmailSmtpEmailProvider(mailSender, emptyProperties);

        assertThatThrownBy(() -> unconfiguredProvider.sendEmail(
                "receiver@example.com", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Sender email is not configured");
    }

    @Test
    @DisplayName("Should classify MailAuthenticationException as non-retryable EmailDeliveryException")
    void shouldClassifyAuthExceptionAsNonRetryable() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("Bad credentials")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailProvider.sendEmail(
                "receiver@example.com", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getMessage()).contains("SMTP authentication failed");
                });
    }

    @Test
    @DisplayName("Should classify MailSendException as retryable EmailDeliveryException")
    void shouldClassifyMailSendExceptionAsRetryable() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("Connection timed out")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailProvider.sendEmail(
                "receiver@example.com", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getMessage()).contains("Failed to deliver email via SMTP");
                });
    }
}
