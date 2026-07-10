package vn.conganh.commercial.feature.auth.email;

public interface EmailProvider {
    void sendEmail(String to, String subject, String contentHtml);
}
