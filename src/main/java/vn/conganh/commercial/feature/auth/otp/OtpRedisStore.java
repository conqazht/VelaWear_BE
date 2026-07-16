package vn.conganh.commercial.feature.auth.otp;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpRedisStore {

    static final String KEY_PREFIX = "auth:otp:v2:";

    private static final DefaultRedisScript<Long> RESERVE_REQUEST = script(
            "redis/otp/reserve-request.lua");
    private static final DefaultRedisScript<Long> PUBLISH_CHALLENGE = script(
            "redis/otp/publish-challenge.lua");
    private static final DefaultRedisScript<Long> VERIFY_AND_ISSUE_PROOF = script(
            "redis/otp/verify-and-issue-proof.lua");
    private static final DefaultRedisScript<Long> CONSUME_PROOF = script(
            "redis/otp/consume-proof.lua");

    private final StringRedisTemplate redisTemplate;

    /**
     * @return zero when the reservation succeeds, otherwise the number of seconds to retry.
     */
    public long reserveRequest(String scopeDigest, Duration cooldown) {
        try {
            long cooldownMillis = cooldown.toMillis();
            Long result = redisTemplate.execute(
                    RESERVE_REQUEST,
                    List.of(cooldownKey(scopeDigest)),
                    String.valueOf(cooldownMillis));
            if (result == null) {
                throw OtpSecurityException.serviceUnavailable();
            }
            return result == 0 ? 0 : millisToSeconds(result);
        } catch (OtpSecurityException exception) {
            throw exception;
        } catch (DataAccessException | IllegalStateException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }
    }

    public void publishChallenge(
            String challengeId,
            String codeDigest,
            String scopeDigest,
            String purpose,
            String actorDigest,
            Duration ttl) {
        try {
            String challengeKey = challengeKey(challengeId);
            Long result = redisTemplate.execute(
                    PUBLISH_CHALLENGE,
                    List.of(
                            challengeKey,
                            activeChallengeKey(scopeDigest),
                            activeProofKey(scopeDigest)),
                    codeDigest,
                    scopeDigest,
                    purpose,
                    actorDigest,
                    String.valueOf(ttl.toMillis()));
            if (result == null || result != 1) {
                throw OtpSecurityException.serviceUnavailable();
            }
        } catch (OtpSecurityException exception) {
            throw exception;
        } catch (DataAccessException | IllegalStateException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }
    }

    public Optional<ChallengeBinding> findChallengeBinding(String challengeId) {
        try {
            List<Object> values = redisTemplate.opsForHash().multiGet(
                    challengeKey(challengeId),
                    List.of("scope", "purpose", "actor"));
            if (values.size() != 3 || values.stream().anyMatch(value -> value == null)) {
                return Optional.empty();
            }
            return Optional.of(new ChallengeBinding(
                    values.get(0).toString(),
                    values.get(1).toString(),
                    values.get(2).toString()));
        } catch (DataAccessException | IllegalStateException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }
    }

    public VerifyOutcome verifyAndIssueProof(
            String challengeId,
            String submittedCodeDigest,
            String scopeDigest,
            String proofDigest,
            Duration proofTtl,
            int maxAttempts,
            Duration attemptsLock) {
        try {
            String challengeKey = challengeKey(challengeId);
            Long result = redisTemplate.execute(
                    VERIFY_AND_ISSUE_PROOF,
                    List.of(
                            challengeKey,
                            activeChallengeKey(scopeDigest),
                            attemptsKey(scopeDigest),
                            proofKey(proofDigest),
                            activeProofKey(scopeDigest)),
                    submittedCodeDigest,
                    scopeDigest,
                    String.valueOf(proofTtl.toMillis()),
                    String.valueOf(maxAttempts),
                    String.valueOf(attemptsLock.toMillis()));
            if (result == null) {
                throw OtpSecurityException.serviceUnavailable();
            }
            if (result == 1) {
                return VerifyOutcome.successful();
            }
            if (result < 0) {
                long retryMillis = Math.max(1, Math.abs(result) - 1);
                return VerifyOutcome.exhausted(millisToSeconds(retryMillis));
            }
            return VerifyOutcome.invalid();
        } catch (OtpSecurityException exception) {
            throw exception;
        } catch (DataAccessException | IllegalStateException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }
    }

    public boolean consumeProof(String proofDigest, String expectedScopeDigest) {
        try {
            Long result = redisTemplate.execute(
                    CONSUME_PROOF,
                    List.of(proofKey(proofDigest), activeProofKey(expectedScopeDigest)),
                    expectedScopeDigest);
            if (result == null) {
                throw OtpSecurityException.serviceUnavailable();
            }
            return result == 1;
        } catch (OtpSecurityException exception) {
            throw exception;
        } catch (DataAccessException | IllegalStateException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }
    }

    static String challengeKey(String challengeId) {
        return KEY_PREFIX + "challenge:" + challengeId;
    }

    static String activeChallengeKey(String scopeDigest) {
        return KEY_PREFIX + "active:" + scopeDigest;
    }

    static String attemptsKey(String scopeDigest) {
        return KEY_PREFIX + "attempts:" + scopeDigest;
    }

    static String cooldownKey(String scopeDigest) {
        return KEY_PREFIX + "cooldown:" + scopeDigest;
    }

    static String proofKey(String proofDigest) {
        return KEY_PREFIX + "proof:" + proofDigest;
    }

    static String activeProofKey(String scopeDigest) {
        return KEY_PREFIX + "proof-active:" + scopeDigest;
    }

    private static long millisToSeconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    public record ChallengeBinding(String scopeDigest, String purpose, String actorDigest) {}

    public record VerifyOutcome(boolean success, boolean attemptsExhausted, long retryAfterSeconds) {

        static VerifyOutcome successful() {
            return new VerifyOutcome(true, false, 0);
        }

        static VerifyOutcome invalid() {
            return new VerifyOutcome(false, false, 0);
        }

        static VerifyOutcome exhausted(long retryAfterSeconds) {
            return new VerifyOutcome(false, true, retryAfterSeconds);
        }
    }
}
