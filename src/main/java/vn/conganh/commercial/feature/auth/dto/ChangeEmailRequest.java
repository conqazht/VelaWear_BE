package vn.conganh.commercial.feature.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeEmailRequest(
        @NotBlank(message = "New email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String newEmail,

        @NotBlank(message = "OTP proof token is required")
        @Size(min = 32, max = 512, message = "OTP proof token is invalid")
        String otpProofToken
) {
    public ChangeEmailRequest(String newEmail) {
        this(newEmail, null);
    }
}
