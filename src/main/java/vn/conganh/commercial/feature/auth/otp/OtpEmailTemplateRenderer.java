package vn.conganh.commercial.feature.auth.otp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import vn.conganh.commercial.util.constant.OtpPurpose;

@Component
public class OtpEmailTemplateRenderer {

    private static final String TEMPLATE_PATH = "templates/email/otp.html";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private final String template;

    public OtpEmailTemplateRenderer() {
        this.template = loadTemplate();
    }

    public String render(OtpPurpose purpose, String code, long ttlMinutes) {
        String subject = getSubject(purpose);
        String purposeTitle = getPurposeTitle(purpose);
        String purposeAction = getPurposeAction(purpose);

        return replacePlaceholders(template, Map.of(
                "subject", escape(subject),
                "purposeTitle", escape(purposeTitle),
                "purposeAction", escape(purposeAction),
                "code", escape(code),
                "ttlMinutes", String.valueOf(ttlMinutes)
        ));
    }

    public String getSubject(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "[Vela Wear] Email Verification Code";
            case FORGOT_PASSWORD -> "[Vela Wear] Password Recovery Code";
            case CHANGE_EMAIL -> "[Vela Wear] Change Email Verification Code";
        };
    }

    private String getPurposeTitle(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "Xác minh địa chỉ Email";
            case FORGOT_PASSWORD -> "Khôi phục mật khẩu";
            case CHANGE_EMAIL -> "Xác minh thay đổi Email";
        };
    }

    private String getPurposeAction(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "hoàn tất đăng ký tài khoản";
            case FORGOT_PASSWORD -> "đặt lại mật khẩu";
            case CHANGE_EMAIL -> "cập nhật địa chỉ email mới";
        };
    }

    private String replacePlaceholders(String source, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            if (replacement == null) {
                throw new IllegalStateException("Unknown OTP email placeholder: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private String loadTemplate() {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load email template " + TEMPLATE_PATH, exception);
        }
    }
}
