package vn.conganh.commercial.feature.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.conganh.commercial.util.constant.OtpPurpose;

@DisplayName("OtpEmailTemplateRenderer")
class OtpEmailTemplateRendererTest {

    private OtpEmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new OtpEmailTemplateRenderer();
    }

    @Test
    @DisplayName("render - tạo đúng template HTML chứa logo VELAWEAR và OTP cho mục đích REGISTER")
    void render_registerPurpose_rendersCorrectHtml() {
        String html = renderer.render(OtpPurpose.REGISTER, "123456", 5);

        assertThat(html)
                .contains("VELAWEAR")
                .contains("Xác minh địa chỉ Email")
                .contains("hoàn tất đăng ký tài khoản")
                .contains("123456")
                .contains("5 phút")
                .contains("© VelaWear. All rights reserved.");
    }

    @Test
    @DisplayName("render - tạo đúng template HTML cho mục đích FORGOT_PASSWORD")
    void render_forgotPasswordPurpose_rendersCorrectHtml() {
        String html = renderer.render(OtpPurpose.FORGOT_PASSWORD, "654321", 10);

        assertThat(html)
                .contains("VELAWEAR")
                .contains("Khôi phục mật khẩu")
                .contains("đặt lại mật khẩu")
                .contains("654321")
                .contains("10 phút");
    }

    @Test
    @DisplayName("render - tạo đúng template HTML cho mục đích CHANGE_EMAIL")
    void render_changeEmailPurpose_rendersCorrectHtml() {
        String html = renderer.render(OtpPurpose.CHANGE_EMAIL, "888999", 3);

        assertThat(html)
                .contains("VELAWEAR")
                .contains("Xác minh thay đổi Email")
                .contains("cập nhật địa chỉ email mới")
                .contains("888999")
                .contains("3 phút");
    }
}
