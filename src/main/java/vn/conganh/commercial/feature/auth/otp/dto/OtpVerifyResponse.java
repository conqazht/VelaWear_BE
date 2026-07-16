package vn.conganh.commercial.feature.auth.otp.dto;

public record OtpVerifyResponse(
        String proofToken,
        long expiresInSeconds
) {}
