package vn.conganh.commercial.feature.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.auth.otp.OtpRedisStore.ChallengeBinding;
import vn.conganh.commercial.feature.auth.otp.OtpRedisStore.VerifyOutcome;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequestResponse;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyResponse;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.security.SecurityHmacService;
import vn.conganh.commercial.security.monitoring.SecurityMetrics;
import vn.conganh.commercial.security.ratelimit.OtpRateLimitProperties;
import vn.conganh.commercial.util.constant.OtpPurpose;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpServiceImpl")
class OtpServiceImplTest {

    private static final String CHALLENGE = "A".repeat(43);
    private static final String PROOF = "B".repeat(43);
    private static final String SCOPE = "scope-digest";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailProvider emailProvider;

    @Mock
    private OtpRedisStore redisStore;

    @Mock
    private SecurityHmacService hmacService;

    @Mock
    private SecurityMetrics securityMetrics;

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        OtpProperties properties = new OtpProperties();
        OtpRateLimitProperties otpRateLimitProperties = new OtpRateLimitProperties();
        otpService = new OtpServiceImpl(
                userRepository,
                emailProvider,
                redisStore,
                hmacService,
                properties,
                otpRateLimitProperties,
                securityMetrics);
    }

    @Test
    @DisplayName("request returns an opaque challenge and publishes only after delivery")
    void requestPublishesChallengeAfterDelivery() {
        OtpRequest request = new OtpRequest(" Test@Example.com ", OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(hmacService.hash("otp-scope", "REGISTER\ntest@example.com\n-"))
                .thenReturn(SCOPE);
        when(hmacService.randomToken()).thenReturn(CHALLENGE);
        when(hmacService.hash(eq("otp-code"), anyString())).thenReturn("code-digest");
        when(redisStore.reserveRequest(SCOPE, Duration.ofSeconds(60))).thenReturn(0L);

        OtpRequestResponse response = otpService.requestOtp(request);

        assertThat(response.challengeId()).isEqualTo(CHALLENGE);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.cooldownSeconds()).isEqualTo(60);
        verify(emailProvider).sendEmail(eq("test@example.com"), anyString(), anyString());
        verify(redisStore).publishChallenge(
                eq(CHALLENGE),
                eq("code-digest"),
                eq(SCOPE),
                eq("REGISTER"),
                eq("-"),
                eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("delivery failure preserves the previous active challenge")
    void deliveryFailureDoesNotPublishNewChallenge() {
        OtpRequest request = new OtpRequest("test@example.com", OtpPurpose.REGISTER);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(hmacService.hash("otp-scope", "REGISTER\ntest@example.com\n-"))
                .thenReturn(SCOPE);
        when(hmacService.randomToken()).thenReturn(CHALLENGE);
        when(hmacService.hash(eq("otp-code"), anyString())).thenReturn("code-digest");
        when(redisStore.reserveRequest(SCOPE, Duration.ofSeconds(60))).thenReturn(0L);
        org.mockito.Mockito.doThrow(new RuntimeException("provider down"))
                .when(emailProvider)
                .sendEmail(eq("test@example.com"), anyString(), anyString());

        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(OtpSecurityException.class)
                .extracting(exception -> ((OtpSecurityException) exception).getCode())
                .isEqualTo("OTP_DELIVERY_UNAVAILABLE");
        verify(redisStore, never()).publishChallenge(
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("forgot-password for an unknown email returns a decoy challenge without delivery")
    void forgotUnknownEmailPublishesDecoy() {
        OtpRequest request = new OtpRequest("missing@example.com", OtpPurpose.FORGOT_PASSWORD);
        when(userRepository.existsByEmail("missing@example.com")).thenReturn(false);
        when(hmacService.hash("otp-scope", "FORGOT_PASSWORD\nmissing@example.com\n-"))
                .thenReturn(SCOPE);
        when(hmacService.randomToken()).thenReturn(CHALLENGE);
        when(hmacService.hash(eq("otp-code"), anyString())).thenReturn("code-digest");
        when(redisStore.reserveRequest(SCOPE, Duration.ofSeconds(60))).thenReturn(0L);

        OtpRequestResponse response = otpService.requestOtp(request);

        assertThat(response.challengeId()).isEqualTo(CHALLENGE);
        verify(emailProvider, never()).sendEmail(anyString(), anyString(), anyString());
        verify(redisStore).publishChallenge(
                eq(CHALLENGE), anyString(), eq(SCOPE), eq("FORGOT_PASSWORD"), eq("-"), any());
    }

    @Test
    @DisplayName("verify returns a proof token after the atomic Redis operation succeeds")
    void verifyReturnsProof() {
        OtpVerifyRequest request = new OtpVerifyRequest(CHALLENGE, "123456");
        when(redisStore.findChallengeBinding(CHALLENGE))
                .thenReturn(java.util.Optional.of(new ChallengeBinding(SCOPE, "REGISTER", "-")));
        when(hmacService.randomToken()).thenReturn(PROOF);
        when(hmacService.hash("otp-proof", PROOF)).thenReturn("proof-digest");
        when(hmacService.hash("otp-code", CHALLENGE + ":123456")).thenReturn("code-digest");
        when(redisStore.verifyAndIssueProof(
                CHALLENGE,
                "code-digest",
                SCOPE,
                "proof-digest",
                Duration.ofSeconds(300),
                5,
                Duration.ofSeconds(600)))
                .thenReturn(VerifyOutcome.successful());

        OtpVerifyResponse response = otpService.verifyOtp(request);

        assertThat(response.proofToken()).isEqualTo(PROOF);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("attempt exhaustion exposes only retry timing, not remaining attempts")
    void verifyRejectsExhaustedAttempts() {
        OtpVerifyRequest request = new OtpVerifyRequest(CHALLENGE, "123456");
        when(redisStore.findChallengeBinding(CHALLENGE))
                .thenReturn(java.util.Optional.of(new ChallengeBinding(SCOPE, "REGISTER", "-")));
        when(hmacService.randomToken()).thenReturn(PROOF);
        when(hmacService.hash("otp-proof", PROOF)).thenReturn("proof-digest");
        when(hmacService.hash("otp-code", CHALLENGE + ":123456")).thenReturn("code-digest");
        when(redisStore.verifyAndIssueProof(
                anyString(), anyString(), anyString(), anyString(), any(), eq(5), any()))
                .thenReturn(VerifyOutcome.exhausted(599));

        assertThatThrownBy(() -> otpService.verifyOtp(request))
                .isInstanceOf(OtpSecurityException.class)
                .satisfies(exception -> {
                    OtpSecurityException otpException = (OtpSecurityException) exception;
                    assertThat(otpException.getCode()).isEqualTo("OTP_ATTEMPTS_EXHAUSTED");
                    assertThat(otpException.getDetails()).containsEntry("retryAfterSeconds", 599L);
                });
    }

    @Test
    @DisplayName("change-email challenge is bound to the authenticated user")
    void changeEmailIsActorBound() {
        OtpVerifyRequest request = new OtpVerifyRequest(CHALLENGE, "123456");
        when(redisStore.findChallengeBinding(CHALLENGE))
                .thenReturn(java.util.Optional.of(new ChallengeBinding(SCOPE, "CHANGE_EMAIL", "actor-1")));
        when(hmacService.hash("otp-actor", "2")).thenReturn("actor-2");

        assertThatThrownBy(() -> otpService.verifyOtp(request, 2L))
                .isInstanceOf(OtpSecurityException.class)
                .extracting(exception -> ((OtpSecurityException) exception).getCode())
                .isEqualTo("OTP_INVALID_OR_EXPIRED");
    }

    @Test
    @DisplayName("proof consumption delegates to the atomic scope-bound operation")
    void consumesProofOnceForExpectedScope() {
        when(hmacService.hash("otp-scope", "REGISTER\ntest@example.com\n-"))
                .thenReturn(SCOPE);
        when(hmacService.hash("otp-proof", PROOF)).thenReturn("proof-digest");
        when(redisStore.consumeProof("proof-digest", SCOPE)).thenReturn(true, false);

        otpService.consumeProof(PROOF, "TEST@example.com", OtpPurpose.REGISTER);

        assertThatThrownBy(() -> otpService.consumeProof(
                PROOF, "test@example.com", OtpPurpose.REGISTER))
                .isInstanceOf(OtpSecurityException.class)
                .extracting(exception -> ((OtpSecurityException) exception).getCode())
                .isEqualTo("OTP_PROOF_INVALID_OR_EXPIRED");
    }
}
