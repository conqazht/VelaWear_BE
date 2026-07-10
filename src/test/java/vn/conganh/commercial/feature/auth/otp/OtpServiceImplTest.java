package vn.conganh.commercial.feature.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import vn.conganh.commercial.exception.AppException;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.OtpPurpose;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpServiceImpl Test")
class OtpServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private EmailProvider emailProvider;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        otpService = new OtpServiceImpl(userRepository, redisTemplate, emailProvider);
        ReflectionTestUtils.setField(otpService, "otpTtlSeconds", 300);
        ReflectionTestUtils.setField(otpService, "otpCooldownSeconds", 60);
        ReflectionTestUtils.setField(otpService, "otpMaxAttempts", 5);
        ReflectionTestUtils.setField(otpService, "otpVerifiedTtlSeconds", 300);
    }

    @Test
    @DisplayName("Should successfully request OTP for registration")
    void shouldSuccessfullyRequestOtpForRegister() {
        // Arrange
        String email = "test@example.com";
        OtpRequest request = new OtpRequest(email, OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Act
        otpService.requestOtp(request);

        // Assert
        verify(valueOperations).set(eq("auth:otp:REGISTER:test@example.com"), anyString(), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("auth:otp:attempts:REGISTER:test@example.com"), eq("0"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq("auth:otp:cooldown:REGISTER:test@example.com"), eq("true"), eq(60L), eq(TimeUnit.SECONDS));
        verify(emailProvider).sendEmail(eq("test@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should clean up OTP state when email delivery fails")
    void shouldCleanUpOtpStateWhenEmailDeliveryFails() {
        // Arrange
        OtpRequest request = new OtpRequest("test@example.com", OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(redisTemplate.hasKey("auth:otp:cooldown:REGISTER:test@example.com")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("provider down"))
                .when(emailProvider)
                .sendEmail(eq("test@example.com"), anyString(), anyString());

        // Act & Assert
        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("provider down");
        verify(redisTemplate).delete("auth:otp:REGISTER:test@example.com");
        verify(redisTemplate).delete("auth:otp:attempts:REGISTER:test@example.com");
        verify(redisTemplate).delete("auth:otp:cooldown:REGISTER:test@example.com");
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException on request OTP for REGISTER if email exists")
    void shouldThrowExceptionWhenRegisterEmailExists() {
        // Arrange
        String email = "test@example.com";
        OtpRequest request = new OtpRequest(email, OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(emailProvider, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw 429 Too Many Requests if cooldown active")
    void shouldThrowExceptionIfCooldownActive() {
        // Arrange
        String email = "test@example.com";
        OtpRequest request = new OtpRequest(email, OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(redisTemplate.hasKey("auth:otp:cooldown:REGISTER:test@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
        verify(emailProvider, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should verify correct OTP successfully")
    void shouldVerifyOtpSuccessfully() {
        // Arrange
        String email = "test@example.com";
        String code = "123456";
        String hashedOtp = hashOtp(code);
        OtpVerifyRequest request = new OtpVerifyRequest(email, OtpPurpose.REGISTER, code);

        when(valueOperations.get("auth:otp:REGISTER:test@example.com")).thenReturn(hashedOtp);
        when(valueOperations.get("auth:otp:attempts:REGISTER:test@example.com")).thenReturn("0");

        // Act
        otpService.verifyOtp(request);

        // Assert
        verify(redisTemplate).delete("auth:otp:REGISTER:test@example.com");
        verify(redisTemplate).delete("auth:otp:attempts:REGISTER:test@example.com");
        verify(valueOperations).set("auth:otp:verified:REGISTER:test@example.com", "true", 300L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should throw InvalidRequestException when verifying non-existent OTP")
    void shouldThrowExceptionIfOtpNotFound() {
        // Arrange
        OtpVerifyRequest request = new OtpVerifyRequest("test@example.com", OtpPurpose.REGISTER, "123456");
        when(valueOperations.get("auth:otp:REGISTER:test@example.com")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> otpService.verifyOtp(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Verification code has expired or is invalid");
    }

    @Test
    @DisplayName("Should throw InvalidRequestException and increment attempts on wrong code")
    void shouldIncrementAttemptsAndThrowOnWrongCode() {
        // Arrange
        String email = "test@example.com";
        OtpVerifyRequest request = new OtpVerifyRequest(email, OtpPurpose.REGISTER, "111111");

        when(valueOperations.get("auth:otp:REGISTER:test@example.com")).thenReturn("someHashedOtp");
        when(valueOperations.get("auth:otp:attempts:REGISTER:test@example.com")).thenReturn("1");
        when(valueOperations.increment("auth:otp:attempts:REGISTER:test@example.com")).thenReturn(2L);

        // Act & Assert
        assertThatThrownBy(() -> otpService.verifyOtp(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid verification code. Remaining attempts: 3");
    }

    @Test
    @DisplayName("Should throw InvalidRequestException and clean up when attempts exceeded")
    void shouldCleanUpAndThrowWhenAttemptsExceeded() {
        // Arrange
        String email = "test@example.com";
        OtpVerifyRequest request = new OtpVerifyRequest(email, OtpPurpose.REGISTER, "111111");

        when(valueOperations.get("auth:otp:REGISTER:test@example.com")).thenReturn("someHashedOtp");
        when(valueOperations.get("auth:otp:attempts:REGISTER:test@example.com")).thenReturn("4");
        when(valueOperations.increment("auth:otp:attempts:REGISTER:test@example.com")).thenReturn(5L);

        // Act & Assert
        assertThatThrownBy(() -> otpService.verifyOtp(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Too many failed verification attempts. Please request a new code.");

        verify(redisTemplate).delete("auth:otp:REGISTER:test@example.com");
        verify(redisTemplate).delete("auth:otp:attempts:REGISTER:test@example.com");
    }

    private String hashOtp(String code) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
