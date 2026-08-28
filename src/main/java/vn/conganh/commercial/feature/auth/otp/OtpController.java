package vn.conganh.commercial.feature.auth.otp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.exception.ServiceUnavailableException;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequestResponse;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyResponse;
import vn.conganh.commercial.security.ClientIpResolver;
import vn.conganh.commercial.security.ratelimit.AuthRateLimitService;
import vn.conganh.commercial.util.constant.OtpPurpose;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Authentication", description = "OTP request and verification endpoints")
public class OtpController {

    private final OtpService otpService;
    private final AuthRateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/request")
    @Operation(summary = "Request email OTP", description = "Generates and sends an OTP to the specified email for registration, forgot password, or changing email.")
    public ResponseEntity<ApiResponse<OtpRequestResponse>> requestOtp(
            @RequestBody @Valid OtpRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        Long actorUserId = extractUserId(jwt);
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        String ipPrefix = clientIpResolver.resolve(httpRequest).rateLimitPrefix();
        Map<String, String> subjects = new LinkedHashMap<>();
        subjects.put("recipient-purpose", normalizedEmail + ':' + request.purpose().name());
        subjects.put("recipient", normalizedEmail);
        subjects.put("ip-recipient", ipPrefix + ':' + normalizedEmail);
        if (request.purpose() == OtpPurpose.CHANGE_EMAIL
                && actorUserId != null) {
            subjects.put("user", String.valueOf(actorUserId));
        }
        try {
            rateLimitService.enforce("otp-request", "OTP_RATE_LIMITED", subjects, ipPrefix);
        } catch (ServiceUnavailableException exception) {
            throw OtpSecurityException.serviceUnavailable();
        }

        OtpRequestResponse response = otpService.requestOtp(request, actorUserId);
        return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                response,
                "Verification code sent successfully",
                LocalDateTime.now()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify email OTP", description = "Verifies a challenge and returns a short-lived, single-use proof token.")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(
            @RequestBody @Valid OtpVerifyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        OtpVerifyResponse response = otpService.verifyOtp(request, extractUserId(jwt));
        return ResponseEntity.ok(new ApiResponse<>(
                HttpStatus.OK.value(),
                response,
                "Verification code verified successfully",
                LocalDateTime.now()));
    }

    private Long extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.longValue();
        }
        if (claim instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
