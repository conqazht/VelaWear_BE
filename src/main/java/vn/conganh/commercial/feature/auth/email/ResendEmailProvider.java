package vn.conganh.commercial.feature.auth.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@Slf4j
@Service
public class ResendEmailProvider implements EmailProvider {

    private final Resend resend;
    private final String fromEmail;

    public ResendEmailProvider(
            Resend resend,
            @Value("${resend.from-email}") String fromEmail) {
        this.resend = resend;
        this.fromEmail = fromEmail;
    }

    @Override
    public String sendEmail(String to, String subject, String contentHtml, String idempotencyKey) {
        log.info("event=email_delivery outcome=started provider=resend");

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(contentHtml)
                .build();

        try {
            CreateEmailResponse response = idempotencyKey == null || idempotencyKey.isBlank()
                    ? resend.emails().send(params)
                    : resend.emails().send(params, Map.of("Idempotency-Key", idempotencyKey));
            if (response == null || response.getId() == null) {
                log.error("event=email_delivery outcome=empty_response provider=resend");
                throw new ServiceUnavailableException("Failed to send email");
            }
            log.info("event=email_delivery outcome=accepted provider=resend");
            return response.getId();
        } catch (ResendException e) {
            boolean retryable = isRetryable(e);
            log.error(
                    "event=email_delivery outcome=rejected provider=resend status={} errorName={} retryable={}",
                    e.getStatusCode(),
                    e.getErrorName(),
                    retryable);
            throw new EmailDeliveryException(providerErrorMessage(e), retryable);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("event=email_delivery outcome=unexpected_failure provider=resend errorType={}",
                    e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Email provider is currently unavailable");
        }
    }

    private boolean isRetryable(ResendException exception) {
        Integer statusCode = exception.getStatusCode();
        if (statusCode == null || statusCode <= 0) {
            return true;
        }
        if ("concurrent_idempotent_requests".equals(exception.getErrorName())) {
            return true;
        }
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private String providerErrorMessage(ResendException exception) {
        StringBuilder message = new StringBuilder("Email provider rejected the request");
        if (exception.getStatusCode() != null) {
            message.append(" (status=").append(exception.getStatusCode());
            if (exception.getErrorName() != null && !exception.getErrorName().isBlank()) {
                message.append(", error=").append(exception.getErrorName());
            }
            message.append(')');
        }
        return message.toString();
    }
}
