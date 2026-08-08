package vn.conganh.commercial.feature.auth.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.UnauthorizedException;
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
import org.springframework.beans.factory.annotation.Autowired;
import vn.conganh.commercial.util.constant.OtpPurpose;

@Slf4j
@Service
public class OtpServiceImpl implements OtpService {

    private static final String NO_ACTOR = "-";

    private final UserRepository userRepository;
    private final EmailProvider emailProvider;
    private final OtpRedisStore redisStore;
    private final SecurityHmacService hmacService;
    private final OtpProperties properties;
    private final OtpRateLimitProperties otpRateLimitProperties;
    private final SecurityMetrics securityMetrics;
    private final OtpEmailTemplateRenderer templateRenderer;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public OtpServiceImpl(
            UserRepository userRepository,
            EmailProvider emailProvider,
            OtpRedisStore redisStore,
            SecurityHmacService hmacService,
            OtpProperties properties,
            OtpRateLimitProperties otpRateLimitProperties,
            SecurityMetrics securityMetrics,
            OtpEmailTemplateRenderer templateRenderer) {
        this.userRepository = userRepository;
        this.emailProvider = emailProvider;
        this.redisStore = redisStore;
        this.hmacService = hmacService;
        this.properties = properties;
        this.otpRateLimitProperties = otpRateLimitProperties;
        this.securityMetrics = securityMetrics;
        this.templateRenderer = templateRenderer;
    }

    public OtpServiceImpl(
            UserRepository userRepository,
            EmailProvider emailProvider,
            OtpRedisStore redisStore,
            SecurityHmacService hmacService,
            OtpProperties properties,
            OtpRateLimitProperties otpRateLimitProperties,
            SecurityMetrics securityMetrics) {
        this(
                userRepository,
                emailProvider,
                redisStore,
                hmacService,
                properties,
                otpRateLimitProperties,
                securityMetrics,
                new OtpEmailTemplateRenderer());
    }

    @Override
    public OtpRequestResponse requestOtp(OtpRequest request) {
        return requestOtp(request, null);
    }

    @Override
    public OtpRequestResponse requestOtp(OtpRequest request, Long actorUserId) {
        String normalizedEmail = normalizeEmail(request.email());
        OtpPurpose purpose = request.purpose();
        requireActorForChangeEmail(purpose, actorUserId);

        boolean deliverEmail = validateAccountState(normalizedEmail, purpose);
        String scopeDigest = scopeDigest(normalizedEmail, purpose, actorUserId);
        long retryAfterSeconds = redisStore.reserveRequest(
                scopeDigest,
                otpRateLimitProperties.getRequestCooldown());
        if (retryAfterSeconds > 0) {
            securityMetrics.otpRequest(purpose.name(), "rate_limited");
            throw OtpSecurityException.rateLimited(retryAfterSeconds);
        }

        String challengeId = hmacService.randomToken();
        String rawCode = generateOtp();
        String codeDigest = hmacService.hash("otp-code", challengeId + ':' + rawCode);
        String actorDigest = actorDigest(purpose, actorUserId);

        if (deliverEmail) {
            Timer.Sample deliveryTimer = securityMetrics.startTimer();
            try {
                long minutes = Math.max(1, properties.getTtlSeconds() / 60);
                emailProvider.sendEmail(
                        normalizedEmail,
                        templateRenderer.getSubject(purpose),
                        templateRenderer.render(purpose, rawCode, minutes));
                securityMetrics.stopOtpDelivery(deliveryTimer, "success");
            } catch (RuntimeException exception) {
                securityMetrics.stopOtpDelivery(deliveryTimer, "failure");
                securityMetrics.otpRequest(purpose.name(), "delivery_unavailable");
                log.warn("event=otp_delivery outcome=failure purpose={} scope={}",
                        purpose, shortDigest(scopeDigest));
                // The reservation intentionally remains. Most importantly, the prior active
                // challenge is not touched until publishChallenge succeeds.
                throw OtpSecurityException.deliveryUnavailable();
            }
        }

        redisStore.publishChallenge(
                challengeId,
                codeDigest,
                scopeDigest,
                purpose.name(),
                actorDigest,
                Duration.ofSeconds(properties.getTtlSeconds()));

        log.info("event=otp_request outcome={} purpose={} scope={}",
                deliverEmail ? "sent" : "decoy", purpose, shortDigest(scopeDigest));
        securityMetrics.otpRequest(purpose.name(), deliverEmail ? "sent" : "decoy");
        return new OtpRequestResponse(
                challengeId,
                properties.getTtlSeconds(),
                otpRateLimitProperties.getRequestCooldown().toSeconds());
    }

    @Override
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request) {
        return verifyOtp(request, null);
    }

    @Override
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest request, Long actorUserId) {
        ChallengeBinding binding = redisStore.findChallengeBinding(request.challengeId())
                .orElseThrow(OtpSecurityException::invalidOrExpired);
        OtpPurpose purpose = parsePurpose(binding.purpose());
        requireActorForChangeEmail(purpose, actorUserId);
        if (!actorMatches(binding.actorDigest(), purpose, actorUserId)) {
            throw OtpSecurityException.invalidOrExpired();
        }

        String rawProofToken = hmacService.randomToken();
        String proofDigest = hmacService.hash("otp-proof", rawProofToken);
        String submittedCodeDigest = hmacService.hash(
                "otp-code",
                request.challengeId() + ':' + request.code());

        VerifyOutcome outcome = redisStore.verifyAndIssueProof(
                request.challengeId(),
                submittedCodeDigest,
                binding.scopeDigest(),
                proofDigest,
                Duration.ofSeconds(properties.getProofTtlSeconds()),
                otpRateLimitProperties.getMaxAttempts(),
                otpRateLimitProperties.getAttemptsLock());

        if (outcome.attemptsExhausted()) {
            securityMetrics.otpVerification(purpose.name(), "attempts_exhausted");
            log.warn("event=otp_verify outcome=attempts_exhausted purpose={} scope={}",
                    purpose, shortDigest(binding.scopeDigest()));
            throw OtpSecurityException.attemptsExhausted(outcome.retryAfterSeconds());
        }
        if (!outcome.success()) {
            securityMetrics.otpVerification(purpose.name(), "invalid");
            log.warn("event=otp_verify outcome=invalid purpose={} scope={}",
                    purpose, shortDigest(binding.scopeDigest()));
            throw OtpSecurityException.invalidOrExpired();
        }

        log.info("event=otp_verify outcome=success purpose={} scope={}",
                purpose, shortDigest(binding.scopeDigest()));
        securityMetrics.otpVerification(purpose.name(), "success");
        return new OtpVerifyResponse(rawProofToken, properties.getProofTtlSeconds());
    }

    @Override
    public void consumeProof(String proofToken, String email, OtpPurpose purpose) {
        consumeProof(proofToken, email, purpose, null);
    }

    @Override
    public void consumeProof(
            String proofToken,
            String email,
            OtpPurpose purpose,
            Long actorUserId) {
        if (proofToken == null || proofToken.isBlank()) {
            throw OtpSecurityException.proofInvalidOrExpired();
        }
        requireActorForChangeEmail(purpose, actorUserId);

        String expectedScopeDigest = scopeDigest(normalizeEmail(email), purpose, actorUserId);
        String proofDigest = hmacService.hash("otp-proof", proofToken);
        if (!redisStore.consumeProof(proofDigest, expectedScopeDigest)) {
            throw OtpSecurityException.proofInvalidOrExpired();
        }
        log.info("event=otp_proof_consume outcome=success purpose={} scope={}",
                purpose, shortDigest(expectedScopeDigest));
    }

    private boolean validateAccountState(String normalizedEmail, OtpPurpose purpose) {
        boolean userExists = userRepository.existsByEmail(normalizedEmail);
        if ((purpose == OtpPurpose.REGISTER || purpose == OtpPurpose.CHANGE_EMAIL) && userExists) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }
        return purpose != OtpPurpose.FORGOT_PASSWORD || userExists;
    }

    private String scopeDigest(String normalizedEmail, OtpPurpose purpose, Long actorUserId) {
        String actor = purpose == OtpPurpose.CHANGE_EMAIL
                ? String.valueOf(actorUserId)
                : NO_ACTOR;
        return hmacService.hash(
                "otp-scope",
                purpose.name() + '\n' + normalizedEmail + '\n' + actor);
    }

    private String actorDigest(OtpPurpose purpose, Long actorUserId) {
        if (purpose != OtpPurpose.CHANGE_EMAIL) {
            return NO_ACTOR;
        }
        return hmacService.hash("otp-actor", String.valueOf(actorUserId));
    }

    private boolean actorMatches(String storedActorDigest, OtpPurpose purpose, Long actorUserId) {
        String expected = actorDigest(purpose, actorUserId);
        return MessageDigest.isEqual(
                storedActorDigest.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    private void requireActorForChangeEmail(OtpPurpose purpose, Long actorUserId) {
        if (purpose == OtpPurpose.CHANGE_EMAIL && actorUserId == null) {
            throw new UnauthorizedException("Authentication is required for changing email");
        }
    }

    private OtpPurpose parsePurpose(String rawPurpose) {
        try {
            return OtpPurpose.valueOf(rawPurpose);
        } catch (IllegalArgumentException exception) {
            throw OtpSecurityException.invalidOrExpired();
        }
    }

    private String generateOtp() {
        return String.valueOf(100_000 + secureRandom.nextInt(900_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String shortDigest(String digest) {
        return digest.substring(0, Math.min(12, digest.length()));
    }
}
