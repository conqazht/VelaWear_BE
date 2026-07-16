package vn.conganh.commercial.feature.auth.otp.dto;

public record OtpRequestResponse(
        String challengeId,
        long expiresInSeconds,
        long cooldownSeconds
) {}
