> **SUPERSEDED (2026-07-16):** Các requirement tạo/consume verified-marker trong
> file này không còn normative. Requirement hiện hành nằm ở
> `openspec/changes/harden-otp-auth-v1/specs/otp-auth-hardening/spec.md`.

## ADDED Requirements

### Requirement: OTP Email Request
The system MUST allow clients to request an email OTP for the supported purposes `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL`.

#### Scenario: Request OTP for supported purpose
- **WHEN** a client submits a normalized-valid email and one of `REGISTER`, `FORGOT_PASSWORD`, or `CHANGE_EMAIL` to the OTP request endpoint
- **THEN** the system creates a purpose-scoped OTP state in Redis and sends an OTP email through the configured email provider

#### Scenario: Reject unsupported OTP purpose
- **WHEN** a client submits an OTP request with an unsupported purpose
- **THEN** the system returns a validation error and MUST NOT send an email

#### Scenario: Resend blocked by cooldown
- **WHEN** a client requests another OTP for the same purpose and email while the resend cooldown key is active
- **THEN** the system returns a rate-limit response and MUST NOT replace the existing OTP state

### Requirement: OTP Storage and Expiration
The system MUST store OTP runtime state in Redis with a short TTL and MUST NOT persist raw OTP codes in PostgreSQL.

#### Scenario: OTP expires
- **WHEN** the OTP TTL has elapsed for a purpose-scoped email
- **THEN** verification with that OTP fails and the system requires a new OTP request

#### Scenario: Raw OTP is not persisted
- **WHEN** the system stores OTP state
- **THEN** it stores only a hashed representation of the OTP and bounded metadata in Redis

### Requirement: OTP Verification
The system MUST verify OTPs by purpose and normalized email, enforce failed-attempt limits, and create a short-lived verified marker after successful verification.

#### Scenario: Verify correct OTP
- **WHEN** a client submits the correct OTP for the same email and purpose before expiration
- **THEN** the system deletes or invalidates the pending OTP state and creates a short-lived verified marker for that purpose and email

#### Scenario: Reject wrong OTP
- **WHEN** a client submits an incorrect OTP before expiration
- **THEN** the system increments the failed-attempt count and returns a validation error

#### Scenario: Too many failed attempts
- **WHEN** a client exceeds the configured failed-attempt limit for a purpose-scoped email
- **THEN** the system invalidates the OTP state and requires a new OTP request

#### Scenario: Reject purpose mismatch
- **WHEN** a client submits an OTP generated for a different purpose
- **THEN** the system rejects the verification and MUST NOT create a verified marker

### Requirement: OTP-Protected Account Actions
The system MUST require a valid purpose-scoped OTP verified marker before finalizing registration, password recovery, or email change actions.

#### Scenario: Register with verified OTP
- **WHEN** a registration request is submitted after successful `REGISTER` OTP verification for the same email
- **THEN** the system creates the user account and consumes the verified marker

#### Scenario: Forgot password with verified OTP
- **WHEN** a password reset request is submitted after successful `FORGOT_PASSWORD` OTP verification for the same email
- **THEN** the system updates the user's password and consumes the verified marker

#### Scenario: Change email with verified OTP
- **WHEN** an authenticated user submits an email change after successful `CHANGE_EMAIL` OTP verification for the new email
- **THEN** the system updates the user's email and consumes the verified marker

#### Scenario: Missing verified marker
- **WHEN** a supported account action is submitted without a valid verified marker for the required purpose and email
- **THEN** the system rejects the action and does not change account state

### Requirement: Email Provider Abstraction
The system MUST send OTP emails through an internal provider abstraction with Resend as the first implementation.

#### Scenario: OTP email sent through Resend provider
- **WHEN** OTP generation succeeds and Resend is configured
- **THEN** the system sends the OTP email through the Resend provider implementation

#### Scenario: Email provider unavailable
- **WHEN** OTP generation succeeds but the configured email provider cannot send the message
- **THEN** the system returns a service error and MUST NOT report the OTP request as successful
