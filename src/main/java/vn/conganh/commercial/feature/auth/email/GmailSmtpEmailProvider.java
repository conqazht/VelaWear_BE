package vn.conganh.commercial.feature.auth.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.feature.auth.email.smtp.SmtpMailProperties;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SmtpMailProperties.class)
public class GmailSmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final SmtpMailProperties properties;

    @Override
    public String sendEmail(String to, String subject, String contentHtml, String idempotencyKey) {
        log.info("event=email_delivery outcome=started provider=gmail-smtp");

        String senderEmail = properties.fromEmail();
        if (senderEmail == null || senderEmail.isBlank()) {
            log.error("event=email_delivery outcome=missing_sender provider=gmail-smtp");
            throw new ServiceUnavailableException("Sender email is not configured");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(new InternetAddress(senderEmail, properties.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(contentHtml, true);

            String messageId = idempotencyKey != null && !idempotencyKey.isBlank()
                    ? idempotencyKey
                    : UUID.randomUUID().toString();

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                mimeMessage.setHeader("X-Idempotency-Key", idempotencyKey);
            }

            mailSender.send(mimeMessage);
            log.info("event=email_delivery outcome=accepted provider=gmail-smtp messageId={}", messageId);
            return messageId;
        } catch (MailAuthenticationException exception) {
            log.error("event=email_delivery outcome=auth_failed provider=gmail-smtp", exception);
            throw new EmailDeliveryException("SMTP authentication failed", false);
        } catch (MailException exception) {
            log.error("event=email_delivery outcome=rejected provider=gmail-smtp errorType={}",
                    exception.getClass().getSimpleName(), exception);
            throw new EmailDeliveryException("Failed to deliver email via SMTP: " + exception.getMessage(), true);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            log.error("event=email_delivery outcome=message_creation_failed provider=gmail-smtp", exception);
            throw new EmailDeliveryException("Failed to construct email message: " + exception.getMessage(), false);
        } catch (RuntimeException exception) {
            log.error("event=email_delivery outcome=unexpected_failure provider=gmail-smtp", exception);
            throw new ServiceUnavailableException("Email provider is currently unavailable");
        }
    }
}
