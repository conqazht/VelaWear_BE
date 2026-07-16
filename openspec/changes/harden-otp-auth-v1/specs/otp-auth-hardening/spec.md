## ADDED Requirements

### Requirement: Scoped One-Time OTP Proof

The system MUST issue an opaque challenge for OTP request, MUST verify only
`challengeId + code`, and MUST issue an opaque proof bound to the original purpose,
normalized email and authenticated actor where applicable.

#### Scenario: Consume proof once

- **WHEN** two final actions concurrently submit the same valid proof and scope
- **THEN** exactly one action consumes the proof and the other receives
  `OTP_PROOF_INVALID_OR_EXPIRED`

#### Scenario: Reject scope substitution

- **WHEN** a proof is submitted for another purpose, email or `CHANGE_EMAIL` actor
- **THEN** the system rejects it without changing account state

#### Scenario: Hide unknown forgot-password account

- **WHEN** `FORGOT_PASSWORD` is requested for an unknown email
- **THEN** the system returns the same status/schema with a decoy challenge and does
  not send email

### Requirement: Atomic OTP State

The system MUST use atomic Redis operations for cooldown reservation, challenge
publication, failed attempts, proof issue and proof consume, and MUST fail closed
when Redis is unavailable.

#### Scenario: Delivery fails after reservation

- **WHEN** the provider rejects delivery after cooldown/quota reservation
- **THEN** reservation remains, previous challenge remains active and the response is
  `OTP_DELIVERY_UNAVAILABLE`

#### Scenario: Resend succeeds

- **WHEN** a new OTP email is accepted
- **THEN** publication invalidates old challenge/proof but does not reset failed
  attempts

### Requirement: Multi-Dimensional Auth Rate Limits

The system MUST enforce configured global/IP buckets before body parsing and
normalized recipient/account/user/session buckets inside the application flow.

#### Scenario: Any bucket denies

- **WHEN** any bucket reaches its configured sliding-window limit
- **THEN** no bucket records the denied request and response is 429 with
  `Retry-After` and `data.retryAfterSeconds` without exposing dimension/remaining
  attempts

### Requirement: Trusted Client Address

The system MUST ignore client-supplied forwarded headers unless the servlet
container is configured with an explicit trusted proxy policy.

#### Scenario: Forge forwarded header in direct mode

- **WHEN** a direct client sends XFF/XFP while strategy is `NONE`
- **THEN** limiter address comes from `getRemoteAddr()` and cookie security comes from
  `request.isSecure()`

#### Scenario: Unsafe native proxy config

- **WHEN** strategy is `NATIVE` with empty or wildcard trusted-proxy regex
- **THEN** application startup fails

### Requirement: Revoke All Sessions

The system MUST compare JWT/session `securityVersion` with PostgreSQL and MUST
increment it transactionally for password reset/change, email change and role
change.

#### Scenario: Redis cleanup fails

- **WHEN** DB version/revocations commit but after-commit Redis cleanup fails
- **THEN** old tokens remain rejected and stale Redis state expires by TTL while a
  bounded log/metric records the failure

#### Scenario: Legacy token

- **WHEN** a JWT has no `securityVersion`
- **THEN** the system returns `401 SESSION_REVOKED`

### Requirement: Bounded Security Observability

The system MUST generate a server-side request ID, MUST NOT log secrets/PII, and
MUST expose security metrics only to ADMIN without unbounded metric tags.

#### Scenario: Read metrics as non-admin

- **WHEN** a non-admin requests `/actuator/metrics/**`
- **THEN** access is denied while health/info remain public

## MODIFIED Requirements

### Requirement: OTP Email Request and Verification

The `auth-otp-email` capability MUST use `auth:otp:v2:*` challenge/proof state. The
legacy verified-marker requirement is superseded and MUST NOT be accepted as proof.

#### Scenario: Submit legacy verified marker

- **WHEN** a final account action submits only OTP v1 verified-marker state without
  a valid `otpProofToken`
- **THEN** the system rejects the action without changing account state
