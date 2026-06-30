## 1. Email Provider Setup

- [x] 1.1 Add Resend configuration properties and environment variables to application config and `.env.example`
- [x] 1.2 Add the HTTP client/dependency needed to call Resend from the backend
- [x] 1.3 Create an internal email provider abstraction for transactional email delivery
- [x] 1.4 Implement `ResendEmailProvider` behind the abstraction
- [x] 1.5 Add tests for successful provider calls and provider failure mapping with Resend mocked

## 2. OTP Core

- [x] 2.1 Add OTP purpose enum with `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL`
- [x] 2.2 Add request/response DTOs for OTP request and OTP verification
- [x] 2.3 Add secure six-digit OTP generation and hashing utilities
- [x] 2.4 Add Redis OTP store operations for pending OTP, cooldown, attempts, and verified marker keys
- [x] 2.5 Enforce OTP TTL, resend cooldown, failed-attempt limit, and successful verification cleanup

## 3. OTP API

- [x] 3.1 Add public `POST /api/v1/auth/otp/request` endpoint
- [x] 3.2 Add public `POST /api/v1/auth/otp/verify` endpoint
- [x] 3.3 Return consistent `ApiResponse` responses and validation errors for unsupported purposes, invalid emails, expired codes, cooldown, and attempts exhaustion
- [x] 3.4 Ensure frontend-visible responses do not expose provider credentials, raw OTP state, or unnecessary account-existence details

## 4. Auth Flow Integration

- [x] 4.1 Require and consume `REGISTER` verified OTP state before account registration is finalized
- [x] 4.2 Add forgot-password reset flow that requires and consumes `FORGOT_PASSWORD` verified OTP state
- [x] 4.3 Add authenticated change-email flow that requires and consumes `CHANGE_EMAIL` verified OTP state for the new email
- [x] 4.4 Keep existing login, refresh, logout, and access-token behavior unchanged

## 5. Documentation and Tests

- [x] 5.1 Update API documentation for OTP request/verify and OTP-protected account actions
- [x] 5.2 Update auth module context with OTP purpose, Redis key, and Resend provider notes
- [x] 5.3 Add service tests for OTP generation, TTL expiry, cooldown, attempts, verification success, and purpose mismatch
- [x] 5.4 Add controller tests for request validation and response contracts
- [x] 5.5 Add integration tests covering Redis-backed OTP state and supported `REGISTER`, `FORGOT_PASSWORD`, and `CHANGE_EMAIL` flows
- [x] 5.6 Run focused auth/OTP tests and the full Maven test suite
