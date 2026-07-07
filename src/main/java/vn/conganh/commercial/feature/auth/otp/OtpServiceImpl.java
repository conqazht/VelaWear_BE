package vn.conganh.commercial.feature.auth.otp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.conganh.commercial.exception.AppException;
import vn.conganh.commercial.exception.DuplicateResourceException;
import vn.conganh.commercial.exception.InvalidRequestException;
import vn.conganh.commercial.feature.auth.email.EmailProvider;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.user.UserRepository;
import vn.conganh.commercial.util.constant.OtpPurpose;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String OTP_KEY_PREFIX = "auth:otp:%s:%s";
    private static final String COOLDOWN_KEY_PREFIX = "auth:otp:cooldown:%s:%s";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:otp:attempts:%s:%s";
    private static final String VERIFIED_KEY_PREFIX = "auth:otp:verified:%s:%s";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final EmailProvider emailProvider;

    @Value("${app.otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${app.otp.cooldown-seconds:60}")
    private long otpCooldownSeconds;

    @Value("${app.otp.max-attempts:5}")
    private int otpMaxAttempts;

    @Value("${app.otp.verified-ttl-seconds:300}")
    private long otpVerifiedTtlSeconds;

    @Override
    public void requestOtp(OtpRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String purposeStr = request.purpose().name();

        // 1. Account existence checks based on purpose
        if (request.purpose() == OtpPurpose.REGISTER) {
            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateResourceException("User", "email", normalizedEmail);
            }
        } else if (request.purpose() == OtpPurpose.CHANGE_EMAIL) {
            if (userRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateResourceException("User", "email", normalizedEmail);
            }
        } else if (request.purpose() == OtpPurpose.FORGOT_PASSWORD) {
            // Mitigate user enumeration: return success directly but do not send email if user doesn't exist
            if (!userRepository.existsByEmail(normalizedEmail)) {
                log.info("[OtpService] Forgot password requested for non-existing email: {}. Silent success.", normalizedEmail);
                return;
            }
        }

        // 2. Cooldown check
        String cooldownKey = String.format(COOLDOWN_KEY_PREFIX, purposeStr, normalizedEmail);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.warn("[OtpService] Cooldown active for email: {}, purpose: {}", normalizedEmail, purposeStr);
            throw new AppException("Please wait before requesting another verification code.", HttpStatus.TOO_MANY_REQUESTS);
        }

        // 3. Generate and hash OTP
        String rawOtp = generateOtp();
        String hashedOtp = hashOtp(rawOtp);

        // 4. Store in Redis
        String otpKey = String.format(OTP_KEY_PREFIX, purposeStr, normalizedEmail);
        String attemptsKey = String.format(ATTEMPTS_KEY_PREFIX, purposeStr, normalizedEmail);

        redisTemplate.opsForValue().set(otpKey, hashedOtp, otpTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(attemptsKey, "0", otpTtlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(cooldownKey, "true", otpCooldownSeconds, TimeUnit.SECONDS);

        // 5. Send Email
        String subject = getEmailSubject(request.purpose());
        String contentHtml = getEmailContentHtml(request.purpose(), rawOtp);
        try {
            emailProvider.sendEmail(normalizedEmail, subject, contentHtml);
        } catch (RuntimeException e) {
            cleanupOtpState(normalizedEmail, request.purpose());
            redisTemplate.delete(cooldownKey);
            throw e;
        }

        log.info("[OtpService] OTP generated and sent to: {}, purpose: {}", normalizedEmail, purposeStr);
    }

    @Override
    public void verifyOtp(OtpVerifyRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        String purposeStr = request.purpose().name();

        String otpKey = String.format(OTP_KEY_PREFIX, purposeStr, normalizedEmail);
        String attemptsKey = String.format(ATTEMPTS_KEY_PREFIX, purposeStr, normalizedEmail);

        // Check if OTP exists
        String storedHashedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedHashedOtp == null) {
            throw new InvalidRequestException("Verification code has expired or is invalid");
        }

        // Check failed attempts
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int currentAttempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;
        if (currentAttempts >= otpMaxAttempts) {
            cleanupOtpState(normalizedEmail, request.purpose());
            throw new InvalidRequestException("Too many failed verification attempts. Please request a new code.");
        }

        // Hash submitted code
        String submittedHash = hashOtp(request.code());

        // Verify
        if (storedHashedOtp.equals(submittedHash)) {
            // Success: cleanup OTP state and create verified marker
            cleanupOtpState(normalizedEmail, request.purpose());
            String verifiedKey = String.format(VERIFIED_KEY_PREFIX, purposeStr, normalizedEmail);
            redisTemplate.opsForValue().set(verifiedKey, "true", otpVerifiedTtlSeconds, TimeUnit.SECONDS);
            log.info("[OtpService] OTP verified successfully for: {}, purpose: {}", normalizedEmail, purposeStr);
        } else {
            // Mismatch: increment attempts
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            int newAttempts = attempts != null ? attempts.intValue() : currentAttempts + 1;
            
            if (newAttempts >= otpMaxAttempts) {
                cleanupOtpState(normalizedEmail, request.purpose());
                throw new InvalidRequestException("Too many failed verification attempts. Please request a new code.");
            } else {
                int remaining = otpMaxAttempts - newAttempts;
                throw new InvalidRequestException("Invalid verification code. Remaining attempts: " + remaining);
            }
        }
    }

    @Override
    public boolean isOtpVerified(String email, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        String verifiedKey = String.format(VERIFIED_KEY_PREFIX, purpose.name(), normalizedEmail);
        return Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey));
    }

    @Override
    public void consumeOtpVerifiedMarker(String email, OtpPurpose purpose) {
        String normalizedEmail = normalizeEmail(email);
        String verifiedKey = String.format(VERIFIED_KEY_PREFIX, purpose.name(), normalizedEmail);
        redisTemplate.delete(verifiedKey);
        log.info("[OtpService] OTP verified marker consumed for: {}, purpose: {}", normalizedEmail, purpose.name());
    }

    private void cleanupOtpState(String normalizedEmail, OtpPurpose purpose) {
        String purposeStr = purpose.name();
        redisTemplate.delete(String.format(OTP_KEY_PREFIX, purposeStr, normalizedEmail));
        redisTemplate.delete(String.format(ATTEMPTS_KEY_PREFIX, purposeStr, normalizedEmail));
    }

    private String generateOtp() {
        SecureRandom secureRandom = new SecureRandom();
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private String hashOtp(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String getEmailSubject(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "[Vela Wear] Email Verification Code";
            case FORGOT_PASSWORD -> "[Vela Wear] Password Recovery Code";
            case CHANGE_EMAIL -> "[Vela Wear] Change Email Verification Code";
        };
    }

    private String getEmailContentHtml(OtpPurpose purpose, String code) {
        String actionText = switch (purpose) {
            case REGISTER -> "complete your registration";
            case FORGOT_PASSWORD -> "reset your password";
            case CHANGE_EMAIL -> "update your email address";
        };

        long minutes = otpTtlSeconds / 60;

        return String.format(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;\">" +
                "  <h2 style=\"color: #333333;\">Vela Wear Verification</h2>" +
                "  <p>Hello,</p>" +
                "  <p>You requested a verification code to %s.</p>" +
                "  <div style=\"background-color: #f5f5f5; padding: 15px; text-align: center; border-radius: 4px; margin: 20px 0;\">" +
                "    <span style=\"font-size: 24px; font-weight: bold; letter-spacing: 5px; color: #1a1a1a;\">%s</span>" +
                "  </div>" +
                "  <p>This code is valid for <strong>%d minutes</strong>. If you did not request this, you can safely ignore this email.</p>" +
                "  <br/>" +
                "  <p>Best regards,<br/>The Vela Wear Team</p>" +
                "</div>",
                actionText, code, minutes
        );
    }
}
