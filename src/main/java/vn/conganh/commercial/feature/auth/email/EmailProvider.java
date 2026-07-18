package vn.conganh.commercial.feature.auth.email;

public interface EmailProvider {

    default String sendEmail(String to, String subject, String contentHtml) {
        return sendEmail(to, subject, contentHtml, null);
    }

    String sendEmail(String to, String subject, String contentHtml, String idempotencyKey);
}
