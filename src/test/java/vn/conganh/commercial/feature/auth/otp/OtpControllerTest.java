package vn.conganh.commercial.feature.auth.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.conganh.commercial.AbstractIntegrationTest;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyResponse;
import vn.conganh.commercial.feature.user.User;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.security.ratelimit.AuthRateLimitService;
import vn.conganh.commercial.util.constant.OtpPurpose;
import vn.conganh.commercial.util.constant.UserGender;

@Transactional
@DisplayName("Module Auth - OtpController")
class OtpControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpService otpService;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private EmailProvider emailProvider;

    @MockitoBean
    private AuthRateLimitService rateLimitService;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("request returns challenge metadata without exposing OTP state")
    void requestOtpRegisterSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(
                                "newuser@velawear.local",
                                OtpPurpose.REGISTER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isString())
                .andExpect(jsonPath("$.data.challengeId", hasLength(43)))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));

        verify(emailProvider).sendEmail(
                eq("newuser@velawear.local"), anyString(), anyString());
    }

    @Test
    @DisplayName("identity limiter Redis outage returns stable OTP service code")
    void requestOtpIdentityLimiterUnavailableReturnsStableCode() throws Exception {
        doThrow(new ServiceUnavailableException("Redis unavailable"))
                .when(rateLimitService)
                .enforce(
                        eq("otp-request"),
                        eq("OTP_RATE_LIMITED"),
                        argThat(subjects -> subjects.containsKey("recipient-purpose")),
                        anyString());

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(
                                "limiter-down@velawear.local",
                                OtpPurpose.REGISTER))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OTP_SERVICE_UNAVAILABLE"));

        verifyNoInteractions(emailProvider);
    }

    @Test
    @DisplayName("registration still rejects an existing account")
    void requestOtpRegisterFailsIfEmailExists() throws Exception {
        User user = new User();
        user.setEmail("existing@velawear.local");
        user.setFullName("Existing User");
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setGender(UserGender.MALE);
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(
                                "existing@velawear.local",
                                OtpPurpose.REGISTER))))
                .andExpect(status().isConflict());

        verifyNoInteractions(emailProvider);
    }

    @Test
    @DisplayName("forgot-password returns a decoy challenge for an unknown account")
    void requestOtpForgotPasswordSilentSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(
                                "nonexistent@velawear.local",
                                OtpPurpose.FORGOT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isString())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        verifyNoInteractions(emailProvider);
    }

    @Test
    @DisplayName("verify exchanges a challenge for a single-use proof")
    void verifyOtpSuccessAndConsumeProofOnce() throws Exception {
        IssuedOtp issued = requestOtp("testverify@velawear.local", OtpPurpose.REGISTER);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(
                                issued.challengeId(),
                                issued.code()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.proofToken", hasLength(43)))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andReturn();

        String proof = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("proofToken").asString();
        otpService.consumeProof(
                proof,
                "testverify@velawear.local",
                OtpPurpose.REGISTER);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> otpService.consumeProof(
                        proof,
                        "testverify@velawear.local",
                        OtpPurpose.REGISTER))
                .isInstanceOf(OtpSecurityException.class)
                .extracting(exception -> ((OtpSecurityException) exception).getCode())
                .isEqualTo("OTP_PROOF_INVALID_OR_EXPIRED");
    }

    @Test
    @DisplayName("five invalid codes lock the scope without disclosing remaining attempts")
    void verifyOtpWrongCodeExhaustsAttempts() throws Exception {
        IssuedOtp issued = requestOtp("testwrong@velawear.local", OtpPurpose.REGISTER);

        for (int attempt = 1; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new OtpVerifyRequest(
                                    issued.challengeId(),
                                    "000000"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_INVALID_OR_EXPIRED"))
                    .andExpect(jsonPath("$.message").value(
                            "Verification code has expired or is invalid"));
        }

        Set<String> attemptsKeys = redisTemplate.keys(OtpRedisStore.KEY_PREFIX + "attempts:*");
        assertThat(attemptsKeys).hasSize(1);
        String attemptsKey = attemptsKeys.iterator().next();
        redisTemplate.expire(attemptsKey, Duration.ofSeconds(2));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(
                                issued.challengeId(),
                                "000000"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXHAUSTED"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());

        assertThat(redisTemplate.getExpire(attemptsKey, TimeUnit.MILLISECONDS))
                .isGreaterThan(590_000L);
    }

    @Test
    @DisplayName("only one concurrent verifier and one concurrent proof consumer can win")
    void verifyAndConsumeAreAtomic() throws Exception {
        IssuedOtp issued = requestOtp("race@velawear.local", OtpPurpose.REGISTER);
        int workers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch verifyStart = new CountDownLatch(1);
            AtomicInteger verifyWinners = new AtomicInteger();
            List<String> proofs = java.util.Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> verifyTasks = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                verifyTasks.add(executor.submit(() -> {
                    await(verifyStart);
                    try {
                        OtpVerifyResponse response = otpService.verifyOtp(new OtpVerifyRequest(
                                issued.challengeId(),
                                issued.code()));
                        proofs.add(response.proofToken());
                        verifyWinners.incrementAndGet();
                    } catch (OtpSecurityException ignored) {
                        // Expected for every loser of the atomic Redis script.
                    }
                }));
            }
            verifyStart.countDown();
            waitFor(verifyTasks);
            assertThat(verifyWinners).hasValue(1);
            assertThat(proofs).hasSize(1);

            CountDownLatch consumeStart = new CountDownLatch(1);
            AtomicInteger consumeWinners = new AtomicInteger();
            List<Future<?>> consumeTasks = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                consumeTasks.add(executor.submit(() -> {
                    await(consumeStart);
                    try {
                        otpService.consumeProof(
                                proofs.getFirst(),
                                "race@velawear.local",
                                OtpPurpose.REGISTER);
                        consumeWinners.incrementAndGet();
                    } catch (OtpSecurityException ignored) {
                        // Expected for every loser of the atomic consume script.
                    }
                }));
            }
            consumeStart.countDown();
            waitFor(consumeTasks);
            assertThat(consumeWinners).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private IssuedOtp requestOtp(String email, OtpPurpose purpose) throws Exception {
        MvcResult requestResult = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(email, purpose))))
                .andExpect(status().isOk())
                .andReturn();
        String challengeId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("data").get("challengeId").asString();

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailProvider).sendEmail(eq(email), anyString(), contentCaptor.capture());
        String code = contentCaptor.getValue().replaceAll(
                "(?s).*letter-spacing: 5px; color: #1a1a1a;\">(\\d{6})</span>.*",
                "$1");
        assertThat(code).matches("\\d{6}");
        return new IssuedOtp(challengeId, code);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void waitFor(List<Future<?>> tasks) throws Exception {
        for (Future<?> task : tasks) {
            task.get();
        }
    }

    private record IssuedOtp(String challengeId, String code) {}
}
