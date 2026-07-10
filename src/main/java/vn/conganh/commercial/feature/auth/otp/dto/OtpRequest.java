package vn.conganh.commercial.feature.auth.otp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.conganh.commercial.util.constant.OtpPurpose;

public record OtpRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        String email,

        @NotNull(message = "OTP purpose is required")
        OtpPurpose purpose
) {}
