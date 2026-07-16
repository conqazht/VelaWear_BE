package vn.conganh.commercial.feature.auth.otp;

import java.util.Map;
import org.springframework.http.HttpStatus;
import vn.conganh.commercial.exception.CodedBusinessException;

public class OtpSecurityException extends CodedBusinessException {

    private OtpSecurityException(
            String code,
            String message,
            HttpStatus status,
            Map<String, Object> details) {
        super(code, message, status, details);
    }

    public static OtpSecurityException invalidOrExpired() {
        return new OtpSecurityException(
                "OTP_INVALID_OR_EXPIRED",
                "Verification code has expired or is invalid",
                HttpStatus.BAD_REQUEST,
                Map.of());
    }

    public static OtpSecurityException attemptsExhausted(long retryAfterSeconds) {
        return new OtpSecurityException(
                "OTP_ATTEMPTS_EXHAUSTED",
                "Too many failed verification attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of("retryAfterSeconds", Math.max(1, retryAfterSeconds)));
    }

    public static OtpSecurityException rateLimited(long retryAfterSeconds) {
        return new OtpSecurityException(
                "OTP_RATE_LIMITED",
                "Please wait before requesting another verification code.",
                HttpStatus.TOO_MANY_REQUESTS,
                Map.of("retryAfterSeconds", Math.max(1, retryAfterSeconds)));
    }

    public static OtpSecurityException proofInvalidOrExpired() {
        return new OtpSecurityException(
                "OTP_PROOF_INVALID_OR_EXPIRED",
                "OTP proof is invalid, expired, or has already been used",
                HttpStatus.BAD_REQUEST,
                Map.of());
    }

    public static OtpSecurityException serviceUnavailable() {
        return new OtpSecurityException(
                "OTP_SERVICE_UNAVAILABLE",
                "OTP verification service is temporarily unavailable",
                HttpStatus.SERVICE_UNAVAILABLE,
                Map.of());
    }

    public static OtpSecurityException deliveryUnavailable() {
        return new OtpSecurityException(
                "OTP_DELIVERY_UNAVAILABLE",
                "Verification email service is temporarily unavailable",
                HttpStatus.SERVICE_UNAVAILABLE,
                Map.of());
    }
}
