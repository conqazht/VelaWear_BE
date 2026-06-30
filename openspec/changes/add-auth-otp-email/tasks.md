## 1. Email Provider Setup

- [ ] 1.1 Add Resend configuration properties and environment variables to application config and `.env.example`
- [ ] 1.2 Add the HTTP client/dependency needed to call Resend from the backend
- [ ] 1.3 Create an internal email provider abstraction for transactional email delivery
- [ ] 1.4 Implement `ResendEmailProvider` behind the abstraction
- [ ] 1.5 Add tests for successful provider calls and provider failure mapping with Resend mocked

## 2. OTP Core

- [ ] 2.1 Add OTP purpose enum with `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL`
- [ ] 2.2 Add request/response DTOs for OTP request and OTP verification
- [ ] 2.3 Add secure six-digit OTP generation and hashing utilities
- [ ] 2.4 Add Redis OTP store operations for pending OTP, cooldown, attempts, and verified marker keys
- [ ] 2.5 Enforce OTP TTL, resend cooldown, failed-attempt limit, and successful verification cleanup

## 3. OTP API

- [ ] 3.1 Add public `POST /api/v1/auth/otp/request` endpoint
- [ ] 3.2 Add public `POST /api/v1/auth/otp/verify` endpoint
- [ ] 3.3 Return consistent `ApiResponse` responses and validation errors for unsupported purposes, invalid emails, expired codes, cooldown, and attempts exhaustion
- [ ] 3.4 Ensure frontend-visible responses do not expose provider credentials, raw OTP state, or unnecessary account-existence details

## 4. Auth Flow Integration

- [ ] 4.1 Require and consume `REGISTER` verified OTP state before account registration is finalized
- [ ] 4.2 Add forgot-password reset flow that requires and consumes `FORGOT_PASSWORD` verified OTP state
- [ ] 4.3 Add authenticated change-email flow that requires and consumes `CHANGE_EMAIL` verified OTP state for the new email
- [ ] 4.4 Keep existing login, refresh, logout, and access-token behavior unchanged

## 5. Documentation and Tests

- [ ] 5.1 Update API documentation for OTP request/verify and OTP-protected account actions
- [ ] 5.2 Update auth module context with OTP purpose, Redis key, and Resend provider notes
- [ ] 5.3 Add service tests for OTP generation, TTL expiry, cooldown, attempts, verification success, and purpose mismatch
- [ ] 5.4 Add controller tests for request validation and response contracts
- [ ] 5.5 Add integration tests covering Redis-backed OTP state and supported `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL` flows
- [ ] 5.6 Run focused auth/OTP tests and the full Maven test suite
