package vn.conganh.commercial.feature.auth.email.smtp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record SmtpMailProperties(
        String fromEmail,
        String fromName
) {
    public SmtpMailProperties {
        if (fromName == null || fromName.isBlank()) {
            fromName = "Vela Wear";
        }
    }
}
