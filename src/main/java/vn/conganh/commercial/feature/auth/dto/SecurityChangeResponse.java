package vn.conganh.commercial.feature.auth.dto;

public record SecurityChangeResponse(
        boolean allSessionsRevoked,
        boolean reauthenticationRequired
) {
    public static SecurityChangeResponse revokedAndReauthenticationRequired() {
        return new SecurityChangeResponse(true, true);
    }
}
