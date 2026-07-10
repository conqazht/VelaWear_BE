package vn.conganh.commercial.feature.auth.otp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.conganh.commercial.dto.ApiResponse;
import vn.conganh.commercial.feature.auth.otp.dto.OtpRequest;
import vn.conganh.commercial.feature.auth.otp.dto.OtpVerifyRequest;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Authentication", description = "OTP request and verification endpoints")
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request")
    @Operation(summary = "Request email OTP", description = "Generates and sends an OTP to the specified email for registration, forgot password, or changing email.")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@RequestBody @Valid OtpRequest request) {
        otpService.requestOtp(request);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), null, "Verification code sent successfully", LocalDateTime.now()));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify email OTP", description = "Verifies the email OTP. On success, a short-lived verification marker is stored in Redis.")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@RequestBody @Valid OtpVerifyRequest request) {
        otpService.verifyOtp(request);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), null, "Verification code verified successfully", LocalDateTime.now()));
    }
}
