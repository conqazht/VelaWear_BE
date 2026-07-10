package vn.conganh.commercial.feature.auth.otp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import vn.conganh.commercial.util.constant.OtpPurpose;

public record OtpVerifyRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        String email,

        @NotNull(message = "OTP purpose is required")
        OtpPurpose purpose,

        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        String code
) {}
