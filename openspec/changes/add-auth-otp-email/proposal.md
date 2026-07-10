## Why

VelaWear currently supports password-based registration and login but does not have a reusable email verification flow for account-sensitive actions. Adding OTP email first gives the auth module a secure notification foundation for registration, password recovery, and email changes before payment notifications are introduced.

## What Changes

- Add Resend-backed transactional email delivery behind an internal email provider abstraction.
- Add OTP request and verification endpoints under `/api/v1/auth/otp`.
- Store OTP state in Redis with a short TTL, resend cooldown, and failed-attempt tracking.
- Support OTP purposes `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL`.
- Require successful OTP verification for the scoped auth/account action before that action is finalized.
- Keep frontend responsibility limited to collecting email/OTP input and calling backend APIs; backend owns OTP generation, validation, storage, and email sending.

## Capabilities

### New Capabilities
- `auth-otp-email`: Auth OTP email flows for registration verification, password recovery, and email change verification.

### Modified Capabilities

## Impact

- Affected APIs: new public OTP request/verify endpoints under `/api/v1/auth/otp`; auth/account flows will consume verified OTP state for supported purposes.
- Affected dependencies/configuration: add Resend email configuration and API key environment variables; keep provider implementation behind an interface.
- Affected Redis usage: new OTP, cooldown, and attempts keys with bounded TTLs.
- Affected auth code: `feature/auth`, user lookup/update paths for forgot password and change email, and validation/error handling.
- Affected tests: auth OTP controller/service tests, Redis TTL/rate-limit behavior, email provider mocking, and supported-purpose scenarios.
