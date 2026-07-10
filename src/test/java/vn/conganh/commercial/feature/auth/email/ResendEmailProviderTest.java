package vn.conganh.commercial.feature.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resend.Resend;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
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
        emailProvider.sendEmail("delivered@resend.dev", "Test Subject", "<p>Test Content</p>");

        // Assert
        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when response is null")
    void shouldThrowExceptionWhenResponseIsNull() throws Exception {
        // Arrange
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> emailProvider.sendEmail("delivered@resend.dev", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Failed to send verification email");
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when exception occurs during call")
    void shouldThrowExceptionWhenApiCallFails() throws Exception {
        // Arrange
        when(emails.send(any(CreateEmailOptions.class))).thenThrow(new RuntimeException("API error"));

        // Act & Assert
        assertThatThrownBy(() -> emailProvider.sendEmail("delivered@resend.dev", "Test Subject", "<p>Test Content</p>"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Verification email provider is currently unavailable");
    }
}
