package vn.conganh.commercial.feature.auth.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
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
    public void sendEmail(String to, String subject, String contentHtml) {
        log.info("[ResendEmailProvider] Sending verification email");

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(to)
                .subject(subject)
                .html(contentHtml)
                .build();

        try {
            CreateEmailResponse response = resend.emails().send(params);
            if (response == null || response.getId() == null) {
                log.error("[ResendEmailProvider] Verification email provider returned an empty response");
                throw new ServiceUnavailableException("Failed to send verification email");
            }
            log.info("[ResendEmailProvider] Verification email accepted by provider");
        } catch (ResendException e) {
            log.error("[ResendEmailProvider] Verification email provider rejected the request; errorType={}",
                    e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Verification email provider is currently unavailable");
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ResendEmailProvider] Unexpected verification email failure; errorType={}",
                    e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Verification email provider is currently unavailable");
        }
    }
}
