package vn.conganh.commercial.feature.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.exception.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResendEmailProvider Test")
class ResendEmailProviderTest {

    @Mock
    private Resend resend;

    @Mock
    private Emails emails;

    private ResendEmailProvider emailProvider;
    private final String fromEmail = "onboarding@resend.dev";

    @BeforeEach
    void setUp() {
        when(resend.emails()).thenReturn(emails);
        emailProvider = new ResendEmailProvider(resend, fromEmail);
    }

    @Test
    @DisplayName("Should send email successfully")
    void shouldSendEmailSuccessfully() throws Exception {
        // Arrange
        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("msg-12345");
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(mockResponse);

        // Act
        String providerMessageId = emailProvider.sendEmail(
                "delivered@resend.dev", "Test Subject", "<p>Test Content</p>");

        // Assert
        assertThat(providerMessageId).isEqualTo("msg-12345");
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    @DisplayName("Should pass an HTTP idempotency key and return provider message id")
    void shouldSendEmailWithIdempotencyKey() throws Exception {
        CreateEmailResponse mockResponse = mock(CreateEmailResponse.class);
        when(mockResponse.getId()).thenReturn("msg-idempotent");
        Map<String, String> options = Map.of("Idempotency-Key", "order-completed/10");
        when(emails.send(any(CreateEmailOptions.class), eq(options))).thenReturn(mockResponse);

        String providerMessageId = emailProvider.sendEmail(
                "delivered@resend.dev",
                "Test Subject",
                "<p>Test Content</p>",
                "order-completed/10");

        assertThat(providerMessageId).isEqualTo("msg-idempotent");
        verify(emails).send(any(CreateEmailOptions.class), eq(options));
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when response is null")
    void shouldThrowExceptionWhenResponseIsNull() throws Exception {
        // Arrange
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> emailProvider.sendEmail("delivered@resend.dev", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Failed to send email");
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when exception occurs during call")
    void shouldThrowExceptionWhenApiCallFails() throws Exception {
        // Arrange
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new RuntimeException("API error"));

        // Act & Assert
        assertThatThrownBy(() -> emailProvider.sendEmail("delivered@resend.dev", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Email provider is currently unavailable");
    }

    @Test
    @DisplayName("Should classify a Resend validation error as permanent")
    void shouldClassifyValidationErrorAsPermanent() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(
                        "provider rejected request",
                        422,
                        "{\"name\":\"validation_error\",\"message\":\"provider details\"}"));

        assertThatThrownBy(() -> emailProvider.sendEmail(
                        "invalid@example.com", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getMessage())
                            .contains("status=422")
                            .contains("validation_error")
                            .doesNotContain("provider details");
                });
    }

    @Test
    @DisplayName("Should classify a Resend rate limit as retryable")
    void shouldClassifyRateLimitAsRetryable() throws Exception {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(
                        "provider rate limited request",
                        429,
                        "{\"name\":\"rate_limit_exceeded\",\"message\":\"provider details\"}"));

        assertThatThrownBy(() -> emailProvider.sendEmail(
                        "delivered@resend.dev", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOfSatisfying(EmailDeliveryException.class,
                        exception -> assertThat(exception.isRetryable()).isTrue());
    }
}
