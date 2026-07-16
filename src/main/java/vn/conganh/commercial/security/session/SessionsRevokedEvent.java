package vn.conganh.commercial.security.session;

record SessionsRevokedEvent(
        Long userId,
        long securityVersion,
        SessionRevocationReason reason
) {
}
