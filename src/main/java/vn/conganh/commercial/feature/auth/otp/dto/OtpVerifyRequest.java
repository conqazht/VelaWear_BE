package vn.conganh.commercial.feature.auth.otp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank(message = "OTP challenge is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "OTP challenge is invalid")
        String challengeId,

        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        String code
) {}
